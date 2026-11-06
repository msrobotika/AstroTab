package org.msrobotika.astrotab

import org.junit.Assert.*
import org.junit.Test
import org.msrobotika.astrotab.astro.Astro
import org.msrobotika.astrotab.data.*
import java.io.File
import java.net.*
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.*
import java.util.Date
import javax.net.ssl.*
import kotlin.concurrent.thread
import kotlin.math.abs

class TabletFixesTest {
    private val captured=Utc.parse("2026-11-06T06:10:00Z")
    private val names=listOf("isrg_root_x2","amazon_root_ca1","gts_root_r4")
    private fun roots()=names.map { File("../app/src/main/res/raw/$it.pem").inputStream() }.let { inputs ->
        try { SecureTls.roots(inputs) } finally { inputs.forEach { it.close() } }
    }
    private fun chain(host: String): Array<X509Certificate> = javaClass.getResourceAsStream("/tls/$host-chain.pem")!!.use {
        CertificateFactory.getInstance("X.509").generateCertificates(it).map { cert -> cert as X509Certificate }.toTypedArray()
    }
    // Fecha fijada exclusivamente en pruebas: las hojas reales caducan, no sus evidencias históricas.
    private fun atCapture(store: KeyStore): X509TrustManager {
        val params=PKIXBuilderParameters(store,X509CertSelector()).apply { isRevocationEnabled=false; date=Date(captured) }
        return TrustManagerFactory.getInstance("PKIX").run {
            init(CertPathTrustManagerParameters(params)); trustManagers.filterIsInstance<X509TrustManager>().single()
        }
    }
    private fun localKeys()=KeyStore.getInstance("PKCS12").apply {
        this@TabletFixesTest.javaClass.getResourceAsStream("/tls/localhost-test.p12")!!.use { load(it,"test-only".toCharArray()) }
    }
    private fun localTrust()=KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        load(null,null); setCertificateEntry("local-test",localKeys().getCertificate("localhost"))
    }
    private fun auth(chain: Array<X509Certificate>)=if(chain[0].publicKey.algorithm=="EC") "ECDHE_ECDSA" else "RSA"

    @Test fun exactPublicRootsAndRealHostChainsValidateOnlyWithSupplement() {
        val rootStore=roots()
        val fingerprints=listOf("69729B8E15A86EFC177A57AFB7171DFC64ADD28C2FCA8CF1507E34453CCB1470",
            "8ECDE6884F3D87B1125BA31AC3FCB13D7016DE7F57CC904FE1CB97C6AE98196E",
            "349DFA4058C5E263123B398AE795573C4E1313C83FE68F93556CD5E8031B3C7D")
        fingerprints.forEachIndexed { i,expected ->
            assertEquals(expected,MessageDigest.getInstance("SHA-256").digest(rootStore.getCertificate("public-root-$i").encoded).joinToString("") { "%02X".format(it) })
        }
        val legacy=atCapture(localTrust()) // Almacén reducido sin ninguna de las raíces modernas.
        val extra=atCapture(rootStore)
        val combined=CombinedTrustManager(legacy,extra)
        for(host in listOf("api.open-meteo.com","services.swpc.noaa.gov","ipapi.co")) {
            val certs=chain(host)
            assertThrows(CertificateException::class.java) { legacy.checkServerTrusted(certs,auth(certs)) }
            combined.checkServerTrusted(certs,auth(certs))
            println("Cadena real archivada válida a fecha de captura: $host")
        }
        assertEquals(legacy.acceptedIssuers.size+extra.acceptedIssuers.size,combined.acceptedIssuers.size)
        // Una CA del sistema que no está en el suplemento sigue siendo aceptada.
        val local=arrayOf(localKeys().getCertificate("localhost") as X509Certificate)
        combined.checkServerTrusted(local,"RSA")
    }
    @Test fun shippedTrustRejectsSelfSignedAndTamperedRealChain() {
        val combined=CombinedTrustManager(SecureTls.trustManager(null),atCapture(roots()))
        val selfSigned=arrayOf(localKeys().getCertificate("localhost") as X509Certificate)
        assertThrows(CertificateException::class.java) { combined.checkServerTrusted(selfSigned,"RSA") }
        val certs=chain("ipapi.co")
        val bytes=certs[0].encoded.clone().apply { this[lastIndex]=(this[lastIndex].toInt() xor 1).toByte() }
        val altered=CertificateFactory.getInstance("X.509").generateCertificate(bytes.inputStream()) as X509Certificate
        assertThrows(CertificateException::class.java) { combined.checkServerTrusted(arrayOf(altered)+certs.drop(1),auth(certs)) }
    }
    @Test fun httpsTransportKeepsHostnameVerificationAndTls12() {
        val keys=localKeys()
        val km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keys,"test-only".toCharArray()) }
        val serverContext=SSLContext.getInstance("TLS").apply { init(km.keyManagers,null,null) }
        val client=SSLContext.getInstance("TLS").apply { init(null,arrayOf<TrustManager>(SecureTls.trustManager(localTrust())),null) }
        val factory=Tls12SocketFactory(client.socketFactory)
        (factory.createSocket() as SSLSocket).use {
            assertTrue(it.enabledProtocols.contains("TLSv1.2"))
            assertFalse(it.enabledProtocols.any { p -> p in listOf("SSLv3","TLSv1","TLSv1.1") })
        }
        val verifier=HttpsURLConnection.getDefaultHostnameVerifier()
        for(host in listOf("localhost","127.0.0.1")) {
            val server=serverContext.serverSocketFactory.createServerSocket(0,1,InetAddress.getByName("127.0.0.1")) as SSLServerSocket
            server.enabledProtocols=arrayOf("TLSv1.2"); server.soTimeout=5000
            val serverErrors=java.util.concurrent.ConcurrentLinkedQueue<Exception>()
            val task=thread(name="test-tls-loopback") {
                try { (server.accept() as SSLSocket).use { socket ->
                    socket.soTimeout=5000; socket.startHandshake()
                    val reader=socket.inputStream.bufferedReader()
                    while(!reader.readLine().isNullOrEmpty()) { /* Consumir cabeceras de prueba local. */ }
                    socket.outputStream.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())
                    socket.outputStream.flush()
                } } catch(e: Exception) { serverErrors.add(e) }
            }
            try {
                val transport=HttpsTransport(factory)
                if(host=="localhost") assertEquals("ok",transport.get("https://$host:${server.localPort}/"))
                else assertThrows(java.io.IOException::class.java) { transport.get("https://$host:${server.localPort}/") }
            } finally { server.close(); task.join(6000); assertFalse(task.isAlive) }
            if(host=="localhost") assertTrue(serverErrors.toString(),serverErrors.isEmpty())
        }
        assertSame(verifier,HttpsURLConnection.getDefaultHostnameVerifier())
    }
    @Test fun networkFailuresSurviveThrottleRestartAndAreSafeToLog() {
        val cache=MemoryCache(); val logged=mutableListOf<Throwable>(); var now=captured
        var broken=true
        val place=Location(40.4168,-3.7038,"manual",now)
        val repo=NetworkRepository(cache,Transport { url ->
            if(!broken) return@Transport if(url.contains("ipapi")) """{"latitude":40.4168,"longitude":-3.7038}""" else
                if(url.contains("open-meteo")) """{"hourly":{"time":["2026-11-06T06:00"],"cloud_cover":[20]}}""" else
                    """[["time_tag","Kp","observed"],["2026-11-06T06:00:00Z","2","observed"]]"""
            when {
                url.contains("open-meteo") -> throw SSLHandshakeException("latitude=40.4168&longitude=-3.7038").apply { initCause(CertificateException(url)) }
                url.contains("forecast") -> throw SocketTimeoutException(url)
                else -> throw UnknownHostException(url)
            }
        },onWarning={ _,error -> logged.add(error) }) { now }.apply { enabled=true }
        repo.refresh(place)
        for(label in listOf("certificado no reconocido","sin conexión","tiempo agotado")) assertTrue(repo.status,repo.status.contains(label))
        val previous=repo.status; repo.refresh(place); assertEquals(previous,repo.status)
        assertThrows(UnknownHostException::class.java) { repo.locateByIp(true) }
        assertTrue(repo.status.contains("Ubicación IP: sin conexión"))
        logged.forEach { error ->
            val output=error.stackTraceToString()
            assertFalse(output,output.contains("40.4168") || output.contains("-3.7038") || output.contains("https://"))
            assertNotNull(error.stackTrace)
        }
        val restart=NetworkRepository(cache,Transport { error("No debe consultar") })
        restart.load(place); assertEquals(repo.failures,restart.failures); assertEquals(repo.status,restart.status)
        assertFalse(cache.values["network_errors"]!!.payload.contains("40.4168"))
        broken=false; now+=31*MINUTE; repo.refresh(place); repo.locateByIp(true)
        assertTrue(repo.failures.toString(),repo.failures.isEmpty()); assertNotNull(repo.clouds); assertNotNull(repo.observed)
        assertTrue(repo.status.contains("Consulta completada"))
        assertEquals(NetworkFailure.HOSTNAME,NetworkFailure.from(CertificateException("No subject alternative DNS name matching example")))
        assertEquals(NetworkFailure.TLS,NetworkFailure.from(SSLHandshakeException("handshake_failure")))
        assertEquals(NetworkFailure.CERTIFICATE_DATE,NetworkFailure.from(SSLHandshakeException("date").apply { initCause(CertificateExpiredException()) }))
    }
    @Test fun moonAboveShowsPreviousRiseAndNextSetInMadrid() {
        val now=Utc.parse("2026-11-06T05:01:00Z") // 06:01 Europe/Madrid.
        fun alt(t: Long): Double { val d=Astro.dayNumber(t); return Astro.moonHorizon(d,Astro.gmst(d)-3.7038,40.4168).alt }
        // Schlyter recalculado para Madrid; cruces independientes a 1 ms,
        // malla de 1 min frente a 5 min del motor. No usa informes de hardware.
        assertEquals(10.880471782230163,alt(now),1e-8)
        val events=Astro.horizonEvents(now,::alt)
        assertTrue(events.above)
        assertTrue(events.rise!! in now-24*HOUR until now)
        assertTrue(events.set!! in now+1..now+24*HOUR)
        assertTrue(alt(events.rise- MINUTE)<0 && alt(events.rise+MINUTE)>0)
        assertTrue(alt(events.set-MINUTE)>0 && alt(events.set+MINUTE)<0)
        assertTrue(abs(alt(events.rise))<0.01); assertTrue(abs(alt(events.set))<0.01)
        assertEquals(Utc.parse("2026-11-06T03:59:30Z").toDouble(),events.rise.toDouble(),1000.0)
        assertEquals(Utc.parse("2026-11-06T15:19:58Z").toDouble(),events.set.toDouble(),1000.0)
        println("Luna 06/11 06:01 Madrid: altura=${alt(now)}, última salida=${Date(events.rise)}, puesta=${Date(events.set)}")
        val below=Astro.horizonEvents(events.set+HOUR,::alt)
        assertFalse(below.above); assertTrue(below.rise!!>events.set+HOUR)
    }
    @Test fun noCrossingNeverReturnsWindowBoundary() {
        for(alt in listOf(-11.5,11.5)) {
            val events=Astro.horizonEvents(captured) { alt }
            assertNull(events.rise); assertNull(events.set)
            assertNull(Astro.nextCross(captured,24,0.0,true) { alt })
            assertNull(Astro.lastCross(captured,24,0.0,true) { alt })
        }
        // Cruce real 1 s después de la ventana: no devolver el extremo.
        assertNull(Astro.nextCross(0,24,0.0,true) { (it-24*HOUR-1000).toDouble() })
    }
}
