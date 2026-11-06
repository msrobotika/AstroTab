package org.msrobotika.astrotab

import org.junit.Assert.*
import org.junit.Test
import org.msrobotika.astrotab.data.*
import java.io.File
import java.security.*
import java.security.cert.*
import java.util.Date
import javax.net.ssl.*

class CelestrakTlsTest {
    @Test fun archivedChainIsValidatedWithDocumentedRootAndRejectsTampering() {
        val cert=File("../app/src/main/res/raw/usertrust_rsa.pem").inputStream().use {CertificateFactory.getInstance("X.509").generateCertificate(it)}
        val fingerprint=MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(""){"%02X".format(it)}
        assertEquals("E793C9B02FD8AA13E21C31228ACCB08119643B749C898964B1746D46C3D4CBD2",fingerprint)
        val store=KeyStore.getInstance(KeyStore.getDefaultType()).apply {load(null,null);setCertificateEntry("usertrust",cert)}
        val params=PKIXBuilderParameters(store,X509CertSelector()).apply {isRevocationEnabled=false;date=Date(Utc.parse("2026-11-06T08:00:00Z"))}
        val tm=TrustManagerFactory.getInstance("PKIX").run {init(CertPathTrustManagerParameters(params));trustManagers.filterIsInstance<X509TrustManager>().single()}
        val factory=CertificateFactory.getInstance("X.509")
        val chain=javaClass.getResourceAsStream("/tls/celestrak.org-chain.pem")!!.use {factory.generateCertificates(it).map {c->c as X509Certificate}.toTypedArray()}
        tm.checkServerTrusted(chain,"RSA")
        val bad=chain[0].encoded.clone().apply {this[lastIndex]=(this[lastIndex].toInt() xor 1).toByte()}
        val altered=factory.generateCertificate(bad.inputStream()) as X509Certificate
        assertThrows(CertificateException::class.java){tm.checkServerTrusted(arrayOf(altered)+chain.drop(1),"RSA")}
    }
}
