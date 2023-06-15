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

        private val RTMP_SERVER_URL = "rtmp://172.17.0.3/live/myStream"

        //        private val GSON = Gson()
        private val asyncHttpClient = Dsl.asyncHttpClient()
    }

    private var inputContext: AVFormatContext? = null
    private var sharedSourceDataLine: SourceDataLine? = null
    private val keepWorking = AtomicBoolean(true)
    private val pendingBytes: BlockingQueue<ByteString> = LinkedBlockingQueue(50)

    val startTime = System.currentTimeMillis()
//    private val transcription = StringBuffer(500)

    fun startService(languageCode: String) {
        LoadBalancerRegistry.getDefaultRegistry().register(PickFirstLoadBalancerProvider())

        println("pal STARTED")

        Loader.load(avcodec::class.java)

        playAudioUrl("https://storage.googleapis.com/botmaker-website/site/Canales/welcome.mp3")
        startListeningAudio()
        val executorService = Executors.newFixedThreadPool(2)

        try {
            executorService.submit {
                try {
                    inputContext = AVFormatContext(null)
                    println("pal audioin 1")
                    val ret: Int = avformat.avformat_open_input(inputContext, RTMP_SERVER_URL, null, null)
                    println("pal audioin 2")
                    if (ret < 0) {
                        throw java.lang.RuntimeException("Error opening input stream.")
                    }
                    println("pal audioin 3")
                    val audioFormat = AudioFormat(44000.0f, 16, 1, true, false)
                    println("pal audioin 4")
                    val info = DataLine.Info(SourceDataLine::class.java, audioFormat)
                    println("pal audioin 5")

                    val sourceDataLine: SourceDataLine = AudioSystem.getLine(info) as SourceDataLine
                    sharedSourceDataLine = sourceDataLine

                    sourceDataLine.open(audioFormat)
                    sourceDataLine.start()

                    println("pal audioin 6")

                    val pkt = AVPacket()
                    //                final byte[] buffer = new byte[4096];
                    println("pal Listening audio started in [" + (System.currentTimeMillis() - startTime) + "]")
                    while (keepWorking.get() && avformat.av_read_frame(inputContext, pkt) >= 0) {
                        val data = pkt.data()
                        val size = pkt.size()
                        val audioBuffer = data.position(0).limit(size.toLong()).asBuffer()
                        //                audioBuffer.get(buffer, 0, size);
                        pendingBytes.add(ByteString.copyFrom(audioBuffer))
                        println("pal audioin bytes added")

//                sourceDataLine.write(buffer, 0, size);
                        avcodec.av_packet_unref(pkt)
                    }
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                    keepWorking.set(false)
                }
            }
            executorService.submit {
                try {
                    SpeechClient.create().use { client ->
                        val clientStream = client
                            .streamingRecognizeCallable()
                            .splitCall(TranscriptionObserver(this))

                        clientStream.send(
                            StreamingRecognizeRequest.newBuilder()
                                .setStreamingConfig(
                                    StreamingRecognitionConfig.newBuilder()
                                        .setConfig(
                                            RecognitionConfig.newBuilder()
                                                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                                                .setLanguageCode(languageCode)
                                                .setUseEnhanced(true) //                        .setModel("phone_call")
                                                //                        .setModel("command_and_search")
                                                .setModel("latest_short")
                                                .setSampleRateHertz(44000) // 16000 22000 o 44000
                                                .build()
                                        )
                                        .setInterimResults(true)
                                        .build()
                                )
                                .build()
                        )
                        var audioBytes: ByteString? = null
                        while (keepWorking.get()) {
                            try {
                                audioBytes = pendingBytes.poll(500, TimeUnit.MILLISECONDS)
                            } catch (interruptedException: InterruptedException) {
                                // ok to ignore
                            }
                            if (audioBytes != null) {
                                println("pal trans sent")
                                clientStream.send(
                                    StreamingRecognizeRequest.newBuilder().setAudioContent(audioBytes).build()
                                )
                            }
                        }
                        clientStream.closeSend()
                    }
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                    keepWorking.set(false)
                }
            }

//             waiting till works
//            while (keepWorking.get()) {
//                try {
//                    ThreadUtils.sleep(loopLongWait)
//                } catch (interruptedException: InterruptedException) {
//                    // ok to ignore
//                }
//            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            throw java.lang.RuntimeException(e)
        } finally {
            stopWorking()
            try {
                executorService.shutdown()
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
            println("pal working finished")
        }
    }

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

    private class TranscriptionObserver(jp: JibriPal) : ResponseObserver<StreamingRecognizeResponse?> {

        private var jibriPal: JibriPal = jp

        override fun onStart(controller: StreamController) {
            println("pal Transcription started in [" + (System.currentTimeMillis() - jibriPal.startTime) + "]")
        }

        override fun onResponse(response: StreamingRecognizeResponse?) {
            if (response == null || response.resultsList.isEmpty())
                return

            val bestTranscript = response
                .resultsList
                .stream()
                .filter { obj: StreamingRecognitionResult -> obj.isFinal }
                .map { obj: StreamingRecognitionResult -> obj.alternativesList }
                .flatMap { obj: List<SpeechRecognitionAlternative> -> obj.stream() }
                .map { obj: SpeechRecognitionAlternative -> obj.transcript }
                .limit(1)
                .findFirst()
                .orElse(null) ?: return

            println("pal Transcript [$bestTranscript]")
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
                            println("pal response [$response.responseBody]")

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
        var fsh: File? = null

        try {
            fsh = File.createTempFile("script", ".sh")

            val command = "#!/bin/sh\n/usr/bin/ffmpeg -f pulse -i default -f flv " + RTMP_SERVER_URL + " &\n"

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
            if (fsh != null) {
                try {
                    fsh.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun executeCmd(c: Array<String>): String? {
        var process: Process? = null

        try {
            println("pal executing [" + java.lang.String.join(" ", *c) + "]")
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
