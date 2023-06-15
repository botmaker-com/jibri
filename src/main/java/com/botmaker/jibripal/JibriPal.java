package com.botmaker.jibripal;

import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.cloud.speech.v1.*;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.ThreadUtils;
import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.Response;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avformat;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Loader;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class JibriPal {

    private static volatile JibriPal _instance = null;
    private static final Object LOCK = new Object();

    private static final String RTMP_SERVER_URL = "rtmp://localhost/live/myStream";
    private static final Duration loopLongWait = Duration.ofMillis(250);
    private static final Gson GSON = new Gson();
    private static final AsyncHttpClient asyncHttpClient = Dsl.asyncHttpClient();

    public static JibriPal instance() {
        if (_instance == null) {
            synchronized (LOCK) {
                if (_instance == null) {
                    _instance = new JibriPal();
                }
            }
        }
        return _instance;
    }

    private JibriPal() {
        // nothing to do
    }

    public void startService() {
        System.out.println("STARTED");
        new Worker("en-US");
    }

    private static final class Worker {

        private AVFormatContext inputContext;
        private SourceDataLine sourceDataLine;
        private final AtomicBoolean keepWorking = new AtomicBoolean(true);
        private final BlockingQueue<ByteString> pendingBytes = new LinkedBlockingQueue<>(100);

        private final long startTime = System.currentTimeMillis();
        private final StringBuffer transcription = new StringBuffer(500);


        /**
         * @param languageCode example en-US ([BCP-47](<a href="https://www.rfc-editor.org/rfc/bcp/bcp47.txt">...</a>) language tag)
         */
        Worker(final String languageCode) {
            Loader.load(org.bytedeco.ffmpeg.global.avcodec.class);

            playAudioUrl("https://storage.googleapis.com/botmaker-website/site/Canales/welcome.mp3");
            final ExecutorService executorService = Executors.newFixedThreadPool(2);

            try {
                executorService.submit(() -> {
                    try {
                        inputContext = new AVFormatContext(null);
                        System.out.println("audioin 1");
                        final int ret = avformat.avformat_open_input(inputContext, RTMP_SERVER_URL, null, null);
                        System.out.println("audioin 2");

                        if (ret < 0) {
                            throw new RuntimeException("Error opening input stream.");
                        }
                        System.out.println("audioin 3");

                        final AudioFormat audioFormat = new AudioFormat(44000.0f, 16, 1, true, false);
                        System.out.println("audioin 4");
                        final DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
                        System.out.println("audioin 5");

                        sourceDataLine = (SourceDataLine) AudioSystem.getLine(info);
                        sourceDataLine.open(audioFormat);
                        sourceDataLine.start();
                        System.out.println("audioin 6");

                        final AVPacket pkt = new AVPacket();
//                final byte[] buffer = new byte[4096];

                        System.out.println("Listening audio started in [" + (System.currentTimeMillis() - startTime) + "]");

                        while (keepWorking.get() && avformat.av_read_frame(inputContext, pkt) >= 0) {
                            final BytePointer data = pkt.data();
                            final int size = pkt.size();

                            final ByteBuffer audioBuffer = data.position(0).limit(size).asBuffer();
//                audioBuffer.get(buffer, 0, size);

                            pendingBytes.add(ByteString.copyFrom(audioBuffer));
                            System.out.println("audioin bytes added");

//                sourceDataLine.write(buffer, 0, size);

                            avcodec.av_packet_unref(pkt);
                        }
                    } catch (final Exception e) {
                        e.printStackTrace();
                        keepWorking.set(false);
                    }
                });


                executorService.submit(() -> {
                    try (final SpeechClient client = SpeechClient.create()) {
                        final ClientStream<StreamingRecognizeRequest> clientStream = client
                                .streamingRecognizeCallable()
                                .splitCall(new TranscriptionObserver());

                        clientStream.send(StreamingRecognizeRequest.newBuilder()
                                .setStreamingConfig(StreamingRecognitionConfig.newBuilder()
                                        .setConfig(RecognitionConfig.newBuilder()
                                                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                                                .setLanguageCode(languageCode)
                                                .setUseEnhanced(true)
//                        .setModel("phone_call")
//                        .setModel("command_and_search")
                                                .setModel("latest_short")
                                                .setSampleRateHertz(44000) //16000 22000 o 44000
                                                .build())
                                        .setInterimResults(true)
                                        .build())
                                .build());

                        ByteString audioBytes = null;

                        while (keepWorking.get()) {
                            try {
                                audioBytes = pendingBytes.poll(500, TimeUnit.MILLISECONDS);
                            } catch (final InterruptedException interruptedException) {
                                // ok to ignore
                            }

                            if (audioBytes != null) {
                                System.out.println("trans sent");
                                clientStream.send(StreamingRecognizeRequest.newBuilder().setAudioContent(audioBytes).build());
                            }
                        }

                        clientStream.closeSend();

                    } catch (final Exception e) {
                        e.printStackTrace();
                        keepWorking.set(false);
                    }
                });

                // waiting till works
                while (keepWorking.get()) {
                    try {
                        ThreadUtils.sleep(loopLongWait);
                    } catch (final InterruptedException interruptedException) {
                        // ok to ignore
                    }
                }

            } catch (final Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            } finally {
                stopWorking();

                try {
                    executorService.shutdown();
                } catch (final Exception e) {
                    e.printStackTrace();
                }

                System.out.println("working finished");
            }
        }

        public void endWork() {
            keepWorking.set(false);
        }

        public String getTranscription() {
            return transcription.toString();
        }

        private void stopWorking() {
            if (sourceDataLine != null) {
                try {
                    sourceDataLine.drain();
                } catch (final Exception e) {
                    e.printStackTrace();
                }

                try {
                    sourceDataLine.close();
                } catch (final Exception e) {
                    e.printStackTrace();
                }
            }

            if (inputContext != null) {
                try {
                    avformat.avformat_close_input(inputContext);
                } catch (final Exception e) {
                    e.printStackTrace();
                }
            }
        }

        private final class TranscriptionObserver implements ResponseObserver<StreamingRecognizeResponse> {

            @Override
            public void onStart(final StreamController controller) {
                System.out.println("Transcription started in [" + (System.currentTimeMillis() - startTime) + "]");
            }

            @Override
            public void onResponse(final StreamingRecognizeResponse response) {
                if (response == null || response.getResultsList().isEmpty())
                    return;

                final String bestTranscript = response
                        .getResultsList()
                        .stream()
                        .filter(StreamingRecognitionResult::getIsFinal)
                        .map(StreamingRecognitionResult::getAlternativesList)
                        .flatMap(Collection::stream)
                        .map(SpeechRecognitionAlternative::getTranscript)
                        .limit(1)
                        .findFirst()
                        .orElse(null);

                if (bestTranscript == null)
                    return;

                System.out.println("Transcript [" + bestTranscript + "]");
                transcription.append(bestTranscript);

                asyncHttpClient
                        .preparePost("https://go.botmaker.com/api/v1.0/intent/processMessage")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "application/json")
                        .addHeader("platformContactId", "1")
                        .addHeader("urlEncodedMessageText", "first message") // nodije  | tedijesiquerias | first message
                        .addHeader("chatPlatform", "WEBCHAT")
                        .addHeader("access-token", "eyJhbGciOiJIUzUxMiJ9.eyJidXNpbmVzc0lkIjoiYm90bWFrZXJ2b2ljZWRlbW8iLCJuYW1lIjoiSGVybmFuIExpZW5kbyIsImFwaSI6dHJ1ZSwiaWQiOiJISU9JMTVMUmJoaHh4S3hiNzBUTllnQUo5bGoxIiwiZXhwIjoxODQzOTI2MDgzLCJqdGkiOiJISU9JMTVMUmJoaHh4S3hiNzBUTllnQUo5bGoxIn0.DSzoCpsS1p9NoYftDFu3CWeMpv_EtqwuHDYfyCzeHrvRFTtnQc1ve2eY9KlRY16LLEJ4feZCHPFIaghDleR2Vg")
                        .execute(new AsyncCompletionHandler<Object>() {
                            @Override
                            public Object onCompleted(final Response response) {
                                try {
                                    final Map<String, Object> payload = GSON.fromJson(response.getResponseBody(), Map.class);
                                    final Map<String, Object> response1 = ((List<Map<String, Object>>) payload.get("response")).get(0);
                                    final String audioURL = ((Map<String, Object>) ((Map<String, Object>) response1.get("attachment")).get("payload")).get("url").toString();

                                    System.out.println("audioURL [" + audioURL + "]");

                                } catch (final Exception e) {
                                    e.printStackTrace();
                                }
                                return response;
                            }

                            @Override
                            public void onThrowable(final Throwable t) {
                                t.printStackTrace();
                            }
                        });
            }

            @Override
            public void onComplete() {
//            System.out.println("Transcription completed.");
            }

            @Override
            public void onError(final Throwable throwable) {
                throwable.printStackTrace();
            }
        }

        private void playAudioUrl(final String url) {
            File f = null;
            File fsh = null;

            try {
                f = File.createTempFile("audio", ".mp3");
                fsh = File.createTempFile("script", ".sh");
                FileUtils.copyURLToFile(new URL(url), f);

                FileUtils.write(fsh,
                        "#!/bin/sh\n" +
                                "/usr/bin/ffmpeg -re -i " + f.getAbsolutePath() + " -f s16le -ar 16000 -ac 1 - > /tmp/virtmic\n"
                        , StandardCharsets.UTF_8);

                executeCmd(new String[]{"chmod", "+x", fsh.getAbsolutePath()});

                executeCmd(new String[]{fsh.getAbsolutePath()});

            } catch (final Exception e) {
                e.printStackTrace();
            } finally {
                if (f != null) {
                    try {
                        f.delete();
                    } catch (final Exception e) {
                        e.printStackTrace();
                    }
                }
                if (fsh != null) {
                    try {
                        fsh.delete();
                    } catch (final Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private String executeCmd(final String[] c) {
            Process process = null;

            try {
                System.out.println("executing [" + String.join(" ", c) + "]");

                final ProcessBuilder processBuilder = new ProcessBuilder(c);
//            processBuilder.environment().put("FONTCONFIG_PATH", "/etc/fonts");
                process = processBuilder.start();
                final String output = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
                final String outputErr = IOUtils.toString(process.getErrorStream(), StandardCharsets.UTF_8);

                final int errCode = process.waitFor();

                if (errCode != 0)
                    throw new Exception("Invalid error code in command: " + errCode + ". Output [" + output + "] [" + outputErr + "]");

                return StringUtils.trimToEmpty(output);

            } catch (final Exception e) {
                new Exception("problems executing [" + String.join(" ", c) + "]: " + e.getMessage(), e).printStackTrace();
                throw new RuntimeException(e);
            } finally {
                if (process != null) {
                    try {
                        process.getInputStream().close();
                    } catch (final IOException e) {
                        // ok to ignore
                    }

                    try {
                        process.getOutputStream().close();
                    } catch (final IOException e) {
                        // ok to ignore
                    }

                    try {
                        process.getErrorStream().close();
                    } catch (final IOException e) {
                        // ok to ignore
                    }
                }
            }
        }
    }
}
