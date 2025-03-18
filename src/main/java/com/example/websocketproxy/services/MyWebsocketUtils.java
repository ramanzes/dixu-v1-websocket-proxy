package com.example.websocketproxy.services;

import com.example.websocketproxy.config.WebSocketConfig;
import com.example.websocketproxy.services.logsandexceptions.MyLogger;
import com.example.websocketproxy.services.logsandexceptions.exceptions.NowItIsNotExistExceptions;
import com.github.luben.zstd.ZstdInputStream;
import org.springframework.http.HttpHeaders;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class MyWebsocketUtils {
    private final DeviceSessionManager deviceSessionManager;

    public MyWebsocketUtils(DeviceSessionManager deviceSessionManager) {
        this.deviceSessionManager = deviceSessionManager;
    }

    public DeviceSessionManager getDeviceSessionManager() {
        return deviceSessionManager;
    }

    private String getHeaders(String message) {
        int headerEndIndex = message.indexOf("\r\n\r\n");
        if (headerEndIndex == -1) {
            MyLogger.logServer("No headers found in the response");
            return "";
        }
        String headers = message.substring(0, headerEndIndex);
        MyLogger.logServer(headers, true);
        return headers;
    }

    // Метод, возвращающий множество поддерживаемых методов сжатия, найденных в заголовке
    public Set<String> getSupportedCompressionMethods(HttpHeaders headers) {
        Set<String> supportedMethods = new HashSet<>();

        // Определяем поддерживаемые методы сжатия
        Set<String> allSupportedMethods = new HashSet<>();
        allSupportedMethods.add("gzip");
        allSupportedMethods.add("deflate");
        allSupportedMethods.add("br");
        allSupportedMethods.add("compress");
        allSupportedMethods.add("zstd");

        // Получаем значение заголовка Content-Encoding
        String encoding = headers.getFirst("Content-Encoding");
        if (encoding != null) {
            encoding = encoding.toLowerCase();

            // Проверяем, какие из поддерживаемых методов присутствуют в заголовке
            for (String method : allSupportedMethods) {
                if (encoding.contains(method)) {
                    supportedMethods.add(method);
                }
            }
        }

        return supportedMethods;
    }

    public static Map<String, String> convertHttpHeadersToMap(HttpHeaders headers) {
        return headers.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, // Ключ (имя заголовка)
                        entry -> String.join(", ", entry.getValue()) // Значение (список значений заголовка объединяем в строку)
                ));
    }


    public static boolean shouldCompress(String contentType,  int contentLength, String fileName) {

        if (contentType == null || contentLength < 512) {  // Не сжимаем мелкие данные
            return false;
        }
        // Проверяем по MIME-типа
        if (contentType.contains("text") ||
                contentType.contains("json") ||
                contentType.contains("xml") ||
                contentType.contains("csv") ||
                contentType.contains("javascript") ||
                contentType.contains("html") ||
                contentType.contains("css") ||
                contentType.equals("application/x-www-form-urlencoded")) {
            return true;
        }

        // Проверяем по расширению файла
        String lowerCaseFileName = fileName.toLowerCase();
        if (lowerCaseFileName.endsWith(".txt") ||
                lowerCaseFileName.endsWith(".log") ||
                lowerCaseFileName.endsWith(".ini") ||
                lowerCaseFileName.endsWith(".csv") ||
                lowerCaseFileName.endsWith(".sql") ||
                lowerCaseFileName.endsWith(".xls")) {
            return true;
        }

        return false; // По умолчанию не сжимаем
    }

/**
 * Отдельный метод для сжатия данных.
 */
        public static byte[] compressData(byte[] data) throws IOException {
            try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                 GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
                gzipOutputStream.write(data);
                // Завершаем сжатие и сбрасываем данные
                gzipOutputStream.finish();
                return byteArrayOutputStream.toByteArray();
            }
        }

    /**
     * Метод для распаковки сжатых данных.
     */
    public static byte[] decompressData(byte[] compressedData) throws IOException {
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(compressedData);
             GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream);
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {

            // Читаем распакованные данные блоками и записываем их в выходной поток
            byte[] buffer = new byte[1024];
            int length;
            while ((length = gzipInputStream.read(buffer)) > 0) {
                byteArrayOutputStream.write(buffer, 0, length);
            }

            // Возвращаем распакованные данные как массив байтов
            return byteArrayOutputStream.toByteArray();
        }
    }


    //отправка потока частями, с сжатием isGzip=True или без isGzip=False
//    public static void sendChunkInputStream(WebSocketSession deviceSession, String requestId, InputStream inputStream, boolean isGzip, int poss) throws Exception {
    public static void sendChunkInputStream(WebSocketSession deviceSession, String requestId, InputStream inputStream, boolean isGzip) throws Exception {
        int initialBufferSize = WebSocketConfig.BUFFER_SIZE;
        byte[] buffer = new byte[initialBufferSize];
        int bytesRead=0;
        boolean isLast = false;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
//            byte[] thisBuff = Arrays.copyOfRange(buffer, poss, bytesRead);
            byte[] thisBuff = Arrays.copyOf(buffer, bytesRead);
//            poss = 0;
            MyLogger.logServByteToString(thisBuff);
            // если передаём параметр сжатия, сжимаем данные, иначе нет
            byte[] chunk = isGzip ? compressData(thisBuff) : thisBuff;
//            isLast = (bytesRead < buffer.length); // Последняя часть, если прочитано меньше буфера
            sendBinaryMessage(deviceSession, requestId, chunk, isLast, isGzip);
            MyLogger.logServer("Sent " + chunk.length + " bytes for requestId: " + requestId + ", isLast: " + isLast + " isGzip=" + isGzip, true);
        }
        //если после всего цикла не было отправлено последнее сообщение, значит поток был кратен размеру буфера. поэтому отправляем пустое последние сообщение. для закрытия потока у клиента.
        if (!isLast) {
            // Если не было отправлено сообщение с isLast = true, отправляем пустое
            sendBinaryMessage(deviceSession, requestId, new byte[0], true, isGzip);
    }
    }

    public synchronized void sendMessage(WebSocketSession session, TextMessage message) throws IOException {
//        synchronized (sendLock) {
//        synchronized (session)
        if (session.isOpen()){
            session.sendMessage(message);
        }
    }

    // Отправка бинарного сообщения через WebSocket. первые байты, флаг сжатия и requestId которые мы получим на стороне клиента всегда в не сжатом виде.
    public synchronized static void sendBinaryMessage(WebSocketSession session, String requestId, byte[] data, boolean isLast, boolean isGzip) throws Exception {
//        synchronized (session) {
        if (session.isOpen()){
            // Создаем буфер для метаданных + данные
            ByteArrayOutputStream messageStream = new ByteArrayOutputStream();
            // Добавляем флаг сжатия
            messageStream.write(isGzip ? 1 : 0); // 1 - сжато, 0 - не сжато

            // Добавляем requestId
            messageStream.write(requestId.getBytes(StandardCharsets.UTF_8));
            // Добавляем сам фрагмент
            messageStream.write(data);





            MyLogger.logServer("only Data to send: " + data.length+" isGzip="+isGzip);
            if (!isLast) {
                if (!isGzip) MyLogger.logServByteToString(data);
                else MyLogger.logSrvDecodeUnGzip(data);
            }


            byte[] messageBytes = messageStream.toByteArray();

           MyLogger.logServer("Длина сообщения вместе флагом и idRequest:"+messageStream.size());
            // Отправляем фрагмент через WebSocket
            session.sendMessage(new BinaryMessage(messageBytes, isLast));
        }
    }


//Метод для потоковой отправки multipart/form-data
public  void sendMultipartFormDataStream(WebSocketSession deviceSession, String requestId, String boundary, String fieldName, MultipartFile file) throws Exception {
        InputStream inputStream = file.getInputStream();
        byte[] buffer = new byte[WebSocketConfig.BUFFER_SIZE];
        int bytesRead;
        boolean isFirstChunk = true;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            ByteArrayOutputStream chunkStream = new ByteArrayOutputStream();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(chunkStream, StandardCharsets.UTF_8), true);

            if (isFirstChunk) {
                // Добавляем границу и метаданные для первого чанка
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"").append(fieldName).append("\"; filename=\"").append(file.getOriginalFilename()).append("\"\r\n");
                writer.append("Content-Type: ").append(file.getContentType()).append("\r\n");
                writer.append("\r\n");
                writer.flush();
                isFirstChunk = false;
            }

            // Добавляем данные файла
            chunkStream.write(buffer, 0, bytesRead);

            // Отправляем чанк через WebSocket
            this.sendBinaryMessage(deviceSession, requestId, chunkStream.toByteArray(), false, false);
        }

        // Завершаем multipart-запрос
        ByteArrayOutputStream finalChunkStream = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(finalChunkStream, StandardCharsets.UTF_8), true);
        writer.append("\r\n").append("--").append(boundary).append("--\r\n");
        writer.flush();

        // Отправляем завершающий чанк
        this.sendBinaryMessage(deviceSession, requestId, finalChunkStream.toByteArray(), true, false);

        inputStream.close();
    }





//    // При отправке текстовых данных
//    byte[] compressedData = compress(requestBody.getBytes(StandardCharsets.UTF_8));
//    sendBinaryMessage(deviceSession, requestId, compressedData);
//    }

    //!!! нужно разжимать все стандартные алгоритмы Content-Encoding, включая brotli, deflate, gzip и compress
    //может при распаковке возвращать как текстовые данные String так и byte[]
    public static Object decompress(byte[] compressedData, HttpHeaders headers) {
        String encoding = headers.getFirst("Content-Encoding");
        if (encoding == null) return compressedData;

        encoding = encoding.toLowerCase();
        if (encoding.contains("gzip")) {
            return decompressGzip(compressedData,headers);
        } else if (encoding.contains("deflate")) {
            return decompressDeflate(compressedData,headers);
        } else if (encoding.contains("br")) {
            return decompressBrotli(compressedData, headers);
        } else if (encoding.contains("compress")) {
            return decompressCompress(compressedData, headers);
        } else if (encoding.contains("zstd")){
            return decompressZstd(compressedData,headers);
        }
        return compressedData; // Если неизвестный формат — вернуть как есть
    }


    public static List<byte[]> splitDataIntoChunks(byte[] data, int chunkSize) {
        List<byte[]> chunks = new ArrayList<>();
        for (int i = 0; i < data.length; i += chunkSize) {
            int end = Math.min(data.length, i + chunkSize);
            byte[] chunk = Arrays.copyOfRange(data, i, end);
            chunks.add(chunk);
        }
        return chunks;
    }


    public static Object returnDecompressData(ByteArrayOutputStream outStream, boolean isText){
        byte[] decompressedData = outStream.toByteArray();
//        String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);
        // Если это текст, преобразуем в строку
//        if (contentType != null && HttpResponse.isTextResponse(contentType)) {
        if (isText) {
            return new String(decompressedData, StandardCharsets.UTF_8);
        }
        return (byte []) decompressedData; // Оставляем бинарные данные
    }

    public static Object decompressGzip(byte[] compressedData, HttpHeaders headers) {
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(compressedData);
             GZIPInputStream gzipStream = new GZIPInputStream(byteStream);
             ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, len);
            }


            String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);

            return returnDecompressData(outStream, HttpResponse.isTextResponse(contentType));

        } catch (Exception e) {
            throw new RuntimeException("Ошибка при разжатии GZIP", e);
        }
    }


    public static Object decompressZstd(byte[] compressedData, HttpHeaders headers) {
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(compressedData);
             ZstdInputStream zstdStream = new ZstdInputStream(byteStream);
             ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int len;
            while ((len = zstdStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, len);
            }

            String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);
            return returnDecompressData(outStream, HttpResponse.isTextResponse(contentType));

        } catch (IOException e) {
            throw new RuntimeException("Ошибка при разжатии Zstandard (zstd)", e);
        }
    }



    public static Object decompressDeflate(byte[] compressedData, HttpHeaders headers) {
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(compressedData);
             InflaterInputStream inflaterStream = new InflaterInputStream(byteStream);
             ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int len;
            while ((len = inflaterStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, len);
            }

            String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);

            return returnDecompressData(outStream, HttpResponse.isTextResponse(contentType));
//            return outStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при разжатии Deflate", e);
        }
    }

    //поддержку compress (LZW,
    public static Object decompressCompress(byte[] compressedData, HttpHeaders headers) {
        MyLogger.logServer("compress LZW");
        throw new NowItIsNotExistExceptions();

//        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(compressedData);
//             LZWInputStream lzwStream = new LZWInputStream(byteStream);
//             ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {
//
//            byte[] buffer = new byte[1024];
//            int len;
//            while ((len = lzwStream.read(buffer)) != -1) {
//                outStream.write(buffer, 0, len);
//            }
//
//            return returnDecompressData(outStream,headers);
////            return outStream.toByteArray();
//        } catch (Exception e) {
//            throw new RuntimeException("Ошибка при разжатии Compress (LZW)", e);
//        }
    }

//import org.brotli.dec.BrotliInputStream;

    //поддержку Brotli (br)
    public static Object decompressBrotli(byte[] compressedData, HttpHeaders headers) {

        MyLogger.logServer("Brotli (br)");
        throw new NowItIsNotExistExceptions();
//        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(compressedData);
//             BrotliInputStream brotliStream = new BrotliInputStream(byteStream);
//             ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {
//
//            byte[] buffer = new byte[1024];
//            int len;
//            while ((len = brotliStream.read(buffer)) != -1) {
//                outStream.write(buffer, 0, len);
//            }
//
//            return returnDecompressData(outStream,headers);
////            return outStream.toByteArray();
//        } catch (Exception e) {
//            throw new RuntimeException("Ошибка при разжатии Brotli", e);
//        }
    }






}




//mrkDown  chatGpt
//        Ты прав, сейчас в коде обрабатываются только `gzip` и `deflate`, но остаются `br` (brotli) и `compress`.
//
//        ### Как добавить поддержку других алгоритмов?
//        1. **Добавить поддержку Brotli (`br`)**
//        - В JDK нет встроенной поддержки Brotli, но есть библиотека [`org.brotli.dec.BrotliInputStream`](https://github.com/google/brotli) от Google.
//        - Установка (Maven):
//        ```xml
//        <dependency>
//         <groupId>org.brotli</groupId>
//         <artifactId>brotli</artifactId>
//         <version>0.1.2</version>
//     </dependency>
//        ```
//        - Код для разжатия:
//        ```java
//     import org.brotli.dec.BrotliInputStream;
//
//public static byte[] decompressBrotli(byte[] compressedData) {
//    try (ByteArrayInputStream byteStream = new ByteArrayInputStream(compressedData);
//         BrotliInputStream brotliStream = new BrotliInputStream(byteStream);
//         ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {
//
//        byte[] buffer = new byte[1024];
//        int len;
//        while ((len = brotliStream.read(buffer)) != -1) {
//            outStream.write(buffer, 0, len);
//        }
//        return outStream.toByteArray();
//    } catch (Exception e) {
//        throw new RuntimeException("Ошибка при разжатии Brotli", e);
//    }
//}
//     ```
//
//             2. **Добавить поддержку Deflate (если нужно отдельно от GZIP)**
//        - `deflate` тоже можно обрабатывать, но он иногда используется без `zlib`-заголовков, поэтому надо тестировать:
//        ```java
//public static byte[] decompressDeflate(byte[] compressedData) {
//    try (ByteArrayInputStream byteStream = new ByteArrayInputStream(compressedData);
//         InflaterInputStream inflaterStream = new InflaterInputStream(byteStream);
//         ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {
//
//        byte[] buffer = new byte[1024];
//        int len;
//        while ((len = inflaterStream.read(buffer)) != -1) {
//            outStream.write(buffer, 0, len);
//        }
//        return outStream.toByteArray();
//    } catch (Exception e) {
//        throw new RuntimeException("Ошибка при разжатии Deflate", e);
//    }
//}
//     ```
//
//             3. **Добавить поддержку `compress` (LZW, редко используется)**
//        - В Java нет стандартной библиотеки для LZW, а `compress` почти не встречается.
//   - Если нужно, можно использовать Apache Commons Compress:
//        ```xml
//        <dependency>
//         <groupId>org.apache.commons</groupId>
//         <artifactId>commons-compress</artifactId>
//         <version>1.21</version>
//     </dependency>
//        ```
//        - Код:
//        ```java
//     import org.apache.commons.compress.compressors.lzw.LZWInputStream;
//
//public static byte[] decompressCompress(byte[] compressedData) {
//    try (ByteArrayInputStream byteStream = new ByteArrayInputStream(compressedData);
//         LZWInputStream lzwStream = new LZWInputStream(byteStream);
//         ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {
//
//        byte[] buffer = new byte[1024];
//        int len;
//        while ((len = lzwStream.read(buffer)) != -1) {
//            outStream.write(buffer, 0, len);
//        }
//        return outStream.toByteArray();
//    } catch (Exception e) {
//        throw new RuntimeException("Ошибка при разжатии Compress (LZW)", e);
//    }
//}
//     ```
//
//             ---
//
//             ### Финальный код обработки:
//Обновим метод `isCompressed` и добавим поддержку всех алгоритмов:
//
//        ```java
//public static boolean isCompressed(HttpHeaders headers) {
//    String encoding = headers.getFirst("Content-Encoding");
//    if (encoding == null) return false;
//    encoding = encoding.toLowerCase();
//    return encoding.contains("gzip") || encoding.contains("deflate") ||
//            encoding.contains("br") || encoding.contains("compress");
//}
//```
//
//Теперь распаковываем данные в зависимости от типа сжатия:
//
//        ```java
//public static byte[] decompress(byte[] compressedData, HttpHeaders headers) {
//    String encoding = headers.getFirst("Content-Encoding");
//    if (encoding == null) return compressedData;
//
//    encoding = encoding.toLowerCase();
//    if (encoding.contains("gzip")) {
//        return decompressGzip(compressedData);
//    } else if (encoding.contains("deflate")) {
//        return decompressDeflate(compressedData);
//    } else if (encoding.contains("br")) {
//        return decompressBrotli(compressedData);
//    } else if (encoding.contains("compress")) {
//        return decompressCompress(compressedData);
//    }
//    return compressedData; // Если неизвестный формат — вернуть как есть
//}
//```
//
//        ---
//
//Теперь твой код сможет разжимать все стандартные алгоритмы `Content-Encoding`, включая `brotli`, `deflate`, `gzip` и `compress`. 🚀