package org.jitsi.jibri.botmaker

import com.google.api.gax.rpc.ResponseObserver
import com.google.api.gax.rpc.StreamController
import com.google.cloud.speech.v1.RecognitionConfig
import com.google.cloud.speech.v1.SpeechClient
import com.google.cloud.speech.v1.StreamingRecognitionConfig
import com.google.cloud.speech.v1.StreamingRecognizeRequest
import com.google.cloud.speech.v1.StreamingRecognizeResponse
import com.google.cloud.speech.v1.StreamingRecognitionResult
import com.google.cloud.speech.v1.SpeechRecognitionAlternative
import com.google.protobuf.ByteString
import io.grpc.LoadBalancerRegistry
import io.grpc.internal.PickFirstLoadBalancerProvider
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.asynchttpclient.AsyncCompletionHandler
import org.asynchttpclient.Dsl
import org.asynchttpclient.Response
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avformat
import org.bytedeco.javacpp.Loader
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

class JibriPal {

    companion object {
        private val ACCESS_TOKEN = "eyJhbGciOiJIUzUxMiJ9.eyJidXNpbmVzc0lkIjoiYm90bWFrZXJ2b2ljZWRl" +
            "bW8iLCJuYW1lIjoiSGVybmFuIExpZW5kbyIsImFwaSI6dHJ1ZSwiaWQiOiJ" +
            "ISU9JMTVMUmJoaHh4S3hiNzBUTllnQUo5bGoxIiwiZXhwIjoxODQzOTI2MD" +
            "gzLCJqdGkiOiJISU9JMTVMUmJoaHh4S3hiNzBUTllnQUo5bGoxIn0.DSzoC" +
            "psS1p9NoYftDFu3CWeMpv_EtqwuHDYfyCzeHrvRFTtnQc1ve2eY9KlRY16LLEJ4feZCHPFIaghDleR2Vg"

        private val RTMP_SERVER_URL = "rtmp://RTMP_IP/live/myStream"

        //        private val GSON = Gson()
        private val asyncHttpClient = Dsl.asyncHttpClient()
    }

    private var inputContext: AVFormatContext? = null
    private var sharedSourceDataLine: SourceDataLine? = null
    private val keepWorking = AtomicBoolean(true)
    private val pendingBytes: BlockingQueue<ByteString> = LinkedBlockingQueue(10_000)

    // val startTime = System.currentTimeMillis()
//    private val transcription = StringBuffer(500)

    fun startService(languageCode: String) {
        println("*** [PAL] startService: " + languageCode)
    }

//    fun startService(languageCode: String) {
//        LoadBalancerRegistry.getDefaultRegistry().register(PickFirstLoadBalancerProvider())
//        Loader.load(avcodec::class.java)
//
//        playAudioUrl("https://storage.googleapis.com/botmaker-website/site/Canales/welcome.mp3")
//        val executorService = Executors.newFixedThreadPool(3)
//
//        try {
//            executorService.submit {
//                try {
//                    startListeningAudio()
//                    println("*** [PAL] startListeningAudio done")
//                } catch (e: java.lang.Exception) {
//                    e.printStackTrace()
//                    keepWorking.set(false)
//                }
//            }
//
//            executorService.submit {
//                try {
//                    inputContext = AVFormatContext(null)
//
//                    val ret: Int = avformat.avformat_open_input(inputContext, RTMP_SERVER_URL, null, null)
//
//                    if (ret < 0) {
//                        throw java.lang.RuntimeException("Error opening input stream.")
//                    }
//
//                    val audioFormat = AudioFormat(16000.0f, 16, 1, true, false)
//                    val info = DataLine.Info(SourceDataLine::class.java, audioFormat)
//
//                    val sourceDataLine: SourceDataLine = AudioSystem.getLine(info) as SourceDataLine
//                    sharedSourceDataLine = sourceDataLine
//
//                    sourceDataLine.open(audioFormat)
//                    sourceDataLine.start()
//
//                    val pkt = AVPacket()
//                    //                final byte[] buffer = new byte[4096];
//                    println("*** [PAL] Listening audio started")
//
//                    while (keepWorking.get() && avformat.av_read_frame(inputContext, pkt) >= 0) {
//                        val data = pkt.data()
//                        val size = pkt.size()
//                        val audioBuffer = data.position(0).limit(size.toLong()).asBuffer()
//                        //                audioBuffer.get(buffer, 0, size);
//                        pendingBytes.add(ByteString.copyFrom(audioBuffer))
//
////                sourceDataLine.write(buffer, 0, size);
//                        avcodec.av_packet_unref(pkt)
//                    }
//                } catch (e: java.lang.Exception) {
//                    e.printStackTrace()
//                    keepWorking.set(false)
//                }
//            }
//            executorService.submit {
//                try {
//                    SpeechClient.create().use { client ->
//                        val clientStream = client
//                            .streamingRecognizeCallable()
//                            .splitCall(TranscriptionObserver())
//
//                        clientStream.send(
//                            StreamingRecognizeRequest.newBuilder()
//                                .setStreamingConfig(
//                                    StreamingRecognitionConfig.newBuilder()
//                                        .setConfig(
//                                            RecognitionConfig.newBuilder()
//                                                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
//                                                .setLanguageCode(languageCode)
//                                                .setUseEnhanced(true) //                        .setModel("phone_call")
//                                                //                        .setModel("command_and_search")
////                                                .setModel("latest_short")
//                                                .setSampleRateHertz(16000) // 16000 22000 o 44000
//                                                .build()
//                                        )
//                                        .setInterimResults(true)
//                                        .build()
//                                )
//                                .build()
//                        )
//                        var audioBytes: ByteString? = null
//                        while (keepWorking.get()) {
//                            try {
//                                audioBytes = pendingBytes.poll(500, TimeUnit.MILLISECONDS)
//                            } catch (interruptedException: InterruptedException) {
//                                // ok to ignore
//                            }
//                            if (audioBytes != null) {
//                                println("*** [PAL] sent")
//                                clientStream.send(
//                                    StreamingRecognizeRequest.newBuilder().setAudioContent(audioBytes).build()
//                                )
//                            }
//                        }
//                        clientStream.closeSend()
//                    }
//                } catch (e: java.lang.Exception) {
//                    e.printStackTrace()
//                    keepWorking.set(false)
//                }
//            }
//        } finally {
//            stopWorking()
//            try {
//                executorService.shutdown()
//            } catch (e: java.lang.Exception) {
//                e.printStackTrace()
//            }
//            println("*** [PAL] working finished **********************************************************")
//        }
//    }

//    fun endWork() {
//        keepWorking.set(false)
//    }

//    fun getTranscription(): String {
//        return transcription.toString()
//    }

    private fun stopWorking() {
        if (sharedSourceDataLine != null) {
            try {
                sharedSourceDataLine!!.drain()
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
            try {
                sharedSourceDataLine!!.close()
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
        if (inputContext != null) {
            try {
                avformat.avformat_close_input(inputContext)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
    }

    private class TranscriptionObserver : ResponseObserver<StreamingRecognizeResponse?> {

        override fun onStart(controller: StreamController) {
            println("*** [PAL] Transcription started")
        }

        override fun onResponse(response: StreamingRecognizeResponse?) {
            if (response != null) {
                println("*** [PAL] Transcription result " + response.resultsList)
                println("*** [PAL] Transcription result " + response.resultsList.size)
            }

            if (response == null || response.resultsList.isEmpty())
                return

            val bestTranscript = response
                .resultsList
                .stream()
//                .filter { obj: StreamingRecognitionResult -> obj.isFinal }
                .map { obj: StreamingRecognitionResult -> obj.alternativesList }
                .flatMap { obj: List<SpeechRecognitionAlternative> -> obj.stream() }
                .map { obj: SpeechRecognitionAlternative -> obj.transcript }
                .limit(1)
                .findFirst()
                .orElse(null) ?: return

            println("*** [PAL] Transcript [$bestTranscript]")
//            transcription.append(bestTranscript)

            asyncHttpClient
                .preparePost("https://go.botmaker.com/api/v1.0/intent/processMessage")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("platformContactId", "1")
                .addHeader("urlEncodedMessageText", "first message") // nodije  | tedijesiquerias | first message
                .addHeader("chatPlatform", "WEBCHAT")
                .addHeader(
                    "access-token",
                    ACCESS_TOKEN
                )
                .execute(object : AsyncCompletionHandler<Any>() {
                    override fun onCompleted(response: Response): Any {
                        try {
                            println("*** [PAL] response [$response.responseBody]")

//                            val payload = GSON.fromJson(response.responseBody, MutableMap::class.java)
//                            val response1 = (payload["response"] as List<Map<String?, Any?>>?)!![0]
//                            val audioURL =
//                                ((response1["attachment"] as Map<String?, Any?>?)!!["payload"] as Map<String?, Any>?)!!["url"].toString()
//                            println("audioURL [$audioURL]")
                        } catch (e: java.lang.Exception) {
                            e.printStackTrace()
                        }
                        return response
                    }

                    override fun onThrowable(t: Throwable) {
                        t.printStackTrace()
                    }
                })
        }

        override fun onComplete() {
//            System.out.println("Transcription completed.");
        }

        override fun onError(throwable: Throwable) {
            throwable.printStackTrace()
        }
    }

    private fun playAudioUrl(url: String) {
        var f: File? = null
        var fsh: File? = null

        try {
            f = File.createTempFile("audio", ".mp3")
            fsh = File.createTempFile("script", ".sh")

            FileUtils.copyURLToFile(URL(url), f)

            val command = "#!/bin/sh\n/usr/bin/ffmpeg -re -i " + f.absolutePath +
                " -f s16le -ar 16000 -ac 1 - > /tmp/virtmic\n"

            FileUtils.write(
                fsh,
                command,
                StandardCharsets.UTF_8
            )

            executeCmd(arrayOf("chmod", "+x", fsh.getAbsolutePath()))

            executeCmd(arrayOf(fsh.getAbsolutePath()))
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (f != null) {
                try {
                    f.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (fsh != null) {
                try {
                    fsh.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun startListeningAudio() {
        try {
            executeCmd(arrayOf("/usr/bin/ffmpeg", "-f", "pulse", "-i", "default", "-f", "flv", RTMP_SERVER_URL))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun executeCmd(c: Array<String>): String? {
        var process: Process? = null

        try {
            println("*** [PAL] executing [" + java.lang.String.join(" ", *c) + "]")
            val processBuilder = ProcessBuilder(*c)

            process = processBuilder.start()

            val output = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8)
            val outputErr = IOUtils.toString(process.getErrorStream(), StandardCharsets.UTF_8)
            val errCode = process.waitFor()

            if (errCode != 0)
                throw Exception("Invalid error code in command: $errCode. Output [$output] [$outputErr]")

            return StringUtils.trimToEmpty(output)
        } catch (e: Exception) {
            Exception("problems executing [" + java.lang.String.join(" ", *c) + "]: " + e.message, e).printStackTrace()
            throw RuntimeException(e)
        } finally {
            if (process != null) {
                try {
                    process.inputStream.close()
                } catch (e: IOException) {
                    // ok to ignore
                }
                try {
                    process.outputStream.close()
                } catch (e: IOException) {
                    // ok to ignore
                }
                try {
                    process.errorStream.close()
                } catch (e: IOException) {
                    // ok to ignore
                }
            }
        }
    }
}
