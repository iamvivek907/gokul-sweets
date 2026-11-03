package com.gokulsweets.restaurant.brand;

import com.gokulsweets.restaurant.security.StaffAuthorizationService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;

import javax.imageio.ImageIO;

@RestController
@RequiredArgsConstructor
public class AppIconController {
    private static final int MAX_PNG_BYTES = 1_100_000;
    private static final long PNG_SIGNATURE = 0x89504e470d0a1a0aL;

    private final JdbcTemplate jdbc;
    private final StaffAuthorizationService staff;

    public record IconVersion(UUID revision) {}

    private void requireOwner() {
        if (!"OWNER_ADMIN".equals(staff.getCurrentStaff().getRole().getName())) {
            throw new AccessDeniedException("Only the owner may change the app icon.");
        }
    }

    @GetMapping("/api/storefront/app-icon")
    public ResponseEntity<IconVersion> version() {
        UUID revision =
                jdbc.query(
                        "SELECT revision FROM storefront_app_icon WHERE id = 1",
                        rows -> rows.next() ? rows.getObject(1, UUID.class) : null);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(new IconVersion(revision));
    }

    @GetMapping("/api/storefront/app-icon/{variant}")
    public ResponseEntity<byte[]> image(@PathVariable String variant) {
        String column =
                switch (variant) {
                    case "192" -> "icon_192";
                    case "512" -> "icon_512";
                    case "maskable" -> "icon_maskable";
                    case "apple" -> "icon_apple";
                    default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                };
        byte[] png =
                jdbc.query(
                        "SELECT " + column + " FROM storefront_app_icon WHERE id = 1",
                        rows -> rows.next() ? rows.getBytes(1) : null);
        if (png == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }

    @PostMapping(value = "/api/admin/app-icon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IconVersion publish(@RequestParam("file") MultipartFile file) throws IOException {
        requireOwner();
        if (file.getSize() < 33 || file.getSize() > MAX_PNG_BYTES) throw invalidIcon();
        byte[] png = file.getBytes();
        ByteBuffer header = ByteBuffer.wrap(png);
        // Reject oversized decoded dimensions before letting ImageIO allocate anything.
        if (header.getLong() != PNG_SIGNATURE
                || header.getInt() != 13
                || header.getInt() != 0x49484452
                || header.getInt() != 512
                || header.getInt() != 512) throw invalidIcon();
        BufferedImage input;
        try {
            input = ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException | RuntimeException ex) {
            throw invalidIcon();
        }
        if (input == null || input.getWidth() != 512 || input.getHeight() != 512)
            throw invalidIcon();
        UUID revision = UUID.randomUUID();
        jdbc.update(
                """
INSERT INTO storefront_app_icon (id, revision, icon_192, icon_512, icon_maskable, icon_apple)
VALUES (1, ?, ?, ?, ?, ?)
ON CONFLICT (id) DO UPDATE SET revision = EXCLUDED.revision,
  icon_192 = EXCLUDED.icon_192, icon_512 = EXCLUDED.icon_512,
  icon_maskable = EXCLUDED.icon_maskable, icon_apple = EXCLUDED.icon_apple
""",
                revision,
                render(input, 192, 192),
                render(input, 512, 512),
                render(input, 512, 288),
                render(input, 180, 180));
        return new IconVersion(revision);
    }

    @DeleteMapping("/api/admin/app-icon")
    public ResponseEntity<Void> restore() {
        requireOwner();
        jdbc.update("DELETE FROM storefront_app_icon WHERE id = 1");
        return ResponseEntity.noContent().build();
    }

    private static byte[] render(BufferedImage input, int size, int inner) throws IOException {
        var output = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        var graphics = output.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, size, size);
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int offset = (size - inner) / 2;
            graphics.drawImage(input, offset, offset, inner, inner, null);
        } finally {
            graphics.dispose();
        }
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(output, "png", bytes);
        return bytes.toByteArray();
    }

    private static ResponseStatusException invalidIcon() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a valid 512 × 512 PNG.");
    }
}
