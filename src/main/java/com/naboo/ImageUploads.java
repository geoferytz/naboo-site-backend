package com.naboo;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
class ImageUploads {
    private final Path folder;
    ImageUploads(@Value("${UPLOAD_DIR:/app/uploads}") String folder) { this.folder=Path.of(folder); }
    record UploadedImage(String url) {}
    @PreAuthorize("hasAnyAuthority('SCOPE_admin','SCOPE_content_manager')")
    @PostMapping(value="/admin/uploads",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    UploadedImage upload(@RequestParam("image") MultipartFile file) throws IOException {
        if(file.isEmpty() || file.getSize()>2_000_000) throw new ResponseStatusException(BAD_REQUEST,"Image must be under 2 MB");
        byte[] bytes=file.getBytes();
        boolean png=bytes.length>=8 && (bytes[0]&255)==137 && bytes[1]==80 && bytes[2]==78 && bytes[3]==71;
        boolean jpeg=bytes.length>=3 && (bytes[0]&255)==255 && (bytes[1]&255)==216 && (bytes[2]&255)==255;
        if(!png && !jpeg) throw new ResponseStatusException(BAD_REQUEST,"Only PNG and JPEG images are supported");
        BufferedImage image;
        try(ImageInputStream input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw new ResponseStatusException(BAD_REQUEST,"Invalid image");
            var reader=readers.next();
            try {
                reader.setInput(input,true,true);
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1 || height<1 || width>4000 || height>4000 || (long)width*height>8_000_000) throw new ResponseStatusException(BAD_REQUEST,"Invalid image dimensions");
                image=reader.read(0);
            } finally { reader.dispose(); }
        }
        if(image==null) throw new ResponseStatusException(BAD_REQUEST,"Invalid image");
        String extension=png?"png":"jpg";
        if(jpeg && image.getType()!=BufferedImage.TYPE_INT_RGB) {
            BufferedImage rgb=new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics=rgb.createGraphics();graphics.drawImage(image,0,0,null);graphics.dispose();image=rgb;
        }
        Files.createDirectories(folder);
        String filename=UUID.randomUUID()+"."+extension;
        Path target=folder.resolve(filename);
        if(!ImageIO.write(image,extension,target.toFile())) throw new ResponseStatusException(BAD_REQUEST,"Image could not be processed");
        return new UploadedImage("/uploads/"+filename);
    }
    @GetMapping("/uploads/{filename}") ResponseEntity<byte[]> image(@PathVariable String filename) throws IOException {
        if(!filename.matches("[a-f0-9-]{36}\\.(png|jpg)")) throw new ResponseStatusException(NOT_FOUND);
        Path path=folder.resolve(filename);
        if(!Files.isRegularFile(path)) throw new ResponseStatusException(NOT_FOUND);
        return ResponseEntity.ok().contentType(filename.endsWith("png")?MediaType.IMAGE_PNG:MediaType.IMAGE_JPEG)
            .header(HttpHeaders.CACHE_CONTROL,"public, max-age=86400").header("X-Content-Type-Options","nosniff").body(Files.readAllBytes(path));
    }
}
