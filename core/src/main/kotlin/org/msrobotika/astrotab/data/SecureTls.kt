package org.msrobotika.astrotab.data

import java.io.InputStream
import java.net.InetAddress
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.*

/** Confianza aditiva: nunca sustituye el almacén Android ni confía en certificados hoja. */
class CombinedTrustManager(private val system: X509TrustManager, private val extra: X509TrustManager): X509TrustManager {
    override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers+extra.acceptedIssuers
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        try { system.checkServerTrusted(chain,authType) }
        catch(systemFailure: CertificateException) {
            try { extra.checkServerTrusted(chain,authType) }
            catch(extraFailure: CertificateException) { extraFailure.addSuppressed(systemFailure); throw extraFailure }
        }
    }
    // Las raíces suplementarias son exclusivamente para servidores.
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = system.checkClientTrusted(chain,authType)
}

object SecureTls {
    fun trustManager(store: KeyStore?): X509TrustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).run {
        init(store); trustManagers.filterIsInstance<X509TrustManager>().single()
    }
    /** El llamante conserva/cierra los streams. Solo se empaquetan raíces públicas autofirmadas. */
    fun roots(inputs: List<InputStream>): KeyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        load(null,null)
        val factory=CertificateFactory.getInstance("X.509")
        inputs.forEachIndexed { index,input ->
            val cert=factory.generateCertificate(input) as X509Certificate
            require(cert.basicConstraints>=0 && cert.subjectX500Principal==cert.issuerX500Principal) { "Se esperaba una raíz CA" }
            cert.verify(cert.publicKey)
            setCertificateEntry("public-root-$index",cert)
        }
    }
    fun socketFactory(roots: KeyStore): SSLSocketFactory {
        val manager=CombinedTrustManager(trustManager(null),trustManager(roots))
        val context=SSLContext.getInstance("TLS").apply { init(null,arrayOf<TrustManager>(manager),null) }
        return Tls12SocketFactory(context.socketFactory)
    }
}

/** Por conexión: TLS1.2 explícito en API23 y TLS1.3 si ya estaba activo. Sin defaults globales. */
class Tls12SocketFactory(private val delegate: SSLSocketFactory): SSLSocketFactory() {
    private fun secure(socket: Socket): Socket = socket.apply {
        if(this is SSLSocket) {
            enabledProtocols=(enabledProtocols.filter { it=="TLSv1.3" }+"TLSv1.2").distinct().toTypedArray()
            // El nombre lo verifica HttpsURLConnection con su verificador estándar (también API23).
        }
    }
    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites
    override fun createSocket(): Socket = secure(delegate.createSocket())
    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket = secure(delegate.createSocket(s,host,port,autoClose))
    override fun createSocket(host: String, port: Int): Socket = secure(delegate.createSocket(host,port))
    override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int): Socket = secure(delegate.createSocket(host,port,local,localPort))
    override fun createSocket(host: InetAddress, port: Int): Socket = secure(delegate.createSocket(host,port))
    override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int): Socket = secure(delegate.createSocket(host,port,local,localPort))
}
