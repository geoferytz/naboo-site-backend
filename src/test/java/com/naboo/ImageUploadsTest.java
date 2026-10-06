package com.naboo;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class ImageUploadsTest {
    @TempDir Path folder;
    @Test void uploadsAndServesReencodedImage() throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",bytes);
        ImageUploads uploads=new ImageUploads(folder.toString());
        String url=uploads.upload(new MockMultipartFile("image","picture.png","image/png",bytes.toByteArray())).url();
        assertTrue(url.matches("/uploads/[a-f0-9-]{36}\\.png"));
        assertEquals("image/png",uploads.image(url.substring(9)).getHeaders().getContentType().toString());
        assertThrows(ResponseStatusException.class,()->uploads.image("../other.txt"));
    }
    @Test void rejectsNonImages() {
        ImageUploads uploads=new ImageUploads(folder.toString());
        assertThrows(ResponseStatusException.class,()->uploads.upload(new MockMultipartFile("image","fake.png","image/png","not an image".getBytes())));
    }
}
