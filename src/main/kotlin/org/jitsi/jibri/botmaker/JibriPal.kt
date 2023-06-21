package org.jitsi.jibri.botmaker

import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.jitsi.utils.logging2.createLogger
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class JibriPal {

    private val logger = createLogger()

    fun startService(callName: String) {
        impl(callName, true);
    }

    fun stopService(callPath: String) {
        // callPath example [/config/recordings/wkstzgnpuotsikpf]
        impl(callPath, false);
    }

    fun impl(param: String, start: Boolean) {
        try {
            logger.info("*** [PAL] calling pal service start:[$start] param:[$param]")

            val v: String = if (start)
                "new";
            else
                "stop";

            val method = HttpGet(
                "http://localhost:8080/" + v + "?id=" + URLEncoder.encode(
                    param,
                    StandardCharsets.UTF_8
                )
            )

            HttpClientBuilder.create().build().use { closeableHttpClient ->
                val response = closeableHttpClient.execute(method)

                if (response.statusLine.statusCode != 200) {
                    throw RuntimeException(
                        "request failed for [" + response.statusLine.statusCode + "]"
                    )
                }
                logger.info("*** [PAL] calling pal service OK:[$start] param:[$param]")
            }
        } catch (e: Exception) {
            logger.error(e);
            logger.error("*** [PAL] service error:[$start] param:[$param]: $e")
        }
    }
}
