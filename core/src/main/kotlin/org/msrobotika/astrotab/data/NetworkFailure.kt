package org.msrobotika.astrotab.data

import java.net.*
import java.security.cert.*
import javax.net.ssl.*

enum class NetworkFailure(val label: String) {
    CERTIFICATE("certificado no reconocido"), CERTIFICATE_DATE("certificado caducado o fecha del sistema incorrecta"),
    HOSTNAME("nombre del servidor no válido"), OFFLINE("sin conexión"), TIMEOUT("tiempo agotado"),
    TLS("no se ha podido establecer una conexión segura"), RESPONSE("respuesta no válida del servicio"), IO("error de conexión o almacenamiento");
    companion object {
        fun from(error: Throwable): NetworkFailure {
            val causes=generateSequence(error) { it.cause }.take(12).toList()
            return when {
                causes.any { it is SocketTimeoutException } -> TIMEOUT
                causes.any { it is UnknownHostException || it is ConnectException || it is NoRouteToHostException || it is SocketException } -> OFFLINE
                causes.any { it is CertificateExpiredException || it is CertificateNotYetValidException } -> CERTIFICATE_DATE
                causes.any { it is SSLPeerUnverifiedException || it is CertificateException && (it.message.orEmpty().contains("subject alternative",true) || it.message.orEmpty().contains("No name matching",true)) } -> HOSTNAME
                causes.any { it is CertificateException || it is CertPathValidatorException } -> CERTIFICATE
                causes.any { it is SSLException } -> TLS
                causes.any { it is java.io.IOException } -> IO
                else -> RESPONSE
            }
        }
        /** Mantiene tipos, causas y stack; mensajes originales pueden incluir URL con coordenadas. */
        fun forLog(error: Throwable, depth: Int=0): Throwable = Exception("${error.javaClass.simpleName}: ${from(error).label}",
            error.cause?.takeIf { depth<10 && it!==error }?.let { forLog(it,depth+1) }).apply { stackTrace=error.stackTrace }
    }
}
