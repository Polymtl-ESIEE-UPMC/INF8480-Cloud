package shared;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.xml.bind.DatatypeConverter;

public final class MD5CheckSum {

    public static String generateChecksum(String filePath) {
        try {
            byte[] fileContents = Files.readAllBytes(Paths.get(filePath));
            byte[] md5Hash = MessageDigest.getInstance("MD5").digest(fileContents);
            return DatatypeConverter.printHexBinary(md5Hash);
        } catch (IOException | NoSuchAlgorithmException e) {
            return null;
        }
    }
}