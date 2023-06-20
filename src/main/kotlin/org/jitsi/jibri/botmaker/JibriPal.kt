package org.jitsi.jibri.botmaker

import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class JibriPal {

    fun startService(languageCode: String) {
        try {
            println("*** [PAL] startService: $languageCode")
            // System.getenv("RTMP_IP")

            val url = "http://localhost:8080/new?l=" + URLEncoder.encode(
                languageCode,
                StandardCharsets.UTF_8
            )

            val method = HttpGet(url)

            val httpClientBuilder = HttpClientBuilder.create()

            httpClientBuilder.build().use { closeableHttpClient ->
                println("*** [PAL] startService url: [$url]")

                val response = closeableHttpClient.execute(method)
                if (response.statusLine.statusCode != 200) {
                    throw RuntimeException(
                        "request failed for [" + response.statusLine.statusCode + "]"
                    )
                }
                println("*** [PAL] startService ok")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("*** [PAL] startService: $e")
        }
    }

    fun stopService(languageCode: String) {
        try {
            println("*** [PAL] stopService: $languageCode")
            // System.getenv("RTMP_IP")

            val url = "http://localhost:8080/stop?l=" + URLEncoder.encode(
                languageCode,
                StandardCharsets.UTF_8
            )

            val method = HttpGet(url)

            val httpClientBuilder = HttpClientBuilder.create()

            httpClientBuilder.build().use { closeableHttpClient ->
                println("*** [PAL] stopService url: [$url]")

                val response = closeableHttpClient.execute(method)
                if (response.statusLine.statusCode != 200) {
                    throw RuntimeException(
                        "request failed for [" + response.statusLine.statusCode + "]"
                    )
                }
                println("*** [PAL] stopService ok")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("*** [PAL] stopService: $e")
        }
    }
}
