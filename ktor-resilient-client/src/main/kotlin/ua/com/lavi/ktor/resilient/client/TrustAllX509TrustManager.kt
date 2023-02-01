package ua.com.lavi.ktor.resilient.client

import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class TrustAllX509TrustManager : X509TrustManager {

    override fun getAcceptedIssuers(): Array<X509Certificate?> = arrayOfNulls(0)

    override fun checkClientTrusted(certs: Array<X509Certificate?>?, authType: String?) {}

    override fun checkServerTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
}