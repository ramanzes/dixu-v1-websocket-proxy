package com.example.websocketproxy.services;

import com.example.websocketproxy.services.logsandexceptions.MyLogger;
import com.example.websocketproxy.services.logsandexceptions.exceptions.NowItIsNotExistExceptions;
import org.apache.commons.compress.compressors.lzw.LZWInputStream;
import org.springframework.http.HttpHeaders;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import org.apache.commons.compress.compressors.lzw.LZWInputStream;

public class MyHttpUtils {

    public static boolean isCompressed(HttpHeaders headers) {
        String encoding = headers.getFirst("Content-Encoding");
        if (encoding == null) return false;
        encoding = encoding.toLowerCase();
        return encoding.contains("gzip") || encoding.contains("deflate") ||
                encoding.contains("br") || encoding.contains("compress");
    }


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
        }
        return compressedData; // Если неизвестный формат — вернуть как есть
    }

    public static Object returnDecompressData(ByteArrayOutputStream outStream, HttpHeaders headers){
        byte[] decompressedData = outStream.toByteArray();
        String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);
        // Если это текст, преобразуем в строку
        if (contentType != null && HttpResponse.isTextResponse(contentType)) {
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

            return returnDecompressData(outStream,headers);

        } catch (Exception e) {
            throw new RuntimeException("Ошибка при разжатии GZIP", e);
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

            return returnDecompressData(outStream,headers);
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