package com.gokulsweets.restaurant.brand;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.security.StaffAuthorizationService;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import javax.imageio.ImageIO;

class AppIconControllerTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final StaffAuthorizationService staff =
            mock(StaffAuthorizationService.class, RETURNS_DEEP_STUBS);
    private final AppIconController controller = new AppIconController(jdbc, staff);

    @Test
    void otherStaffCannotPublishOrRestore() {
        when(staff.getCurrentStaff().getRole().getName()).thenReturn("BRANCH_MANAGER");
        assertThrows(AccessDeniedException.class, () -> controller.publish(upload(new byte[0])));
        assertThrows(AccessDeniedException.class, controller::restore);
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsWrongSizeBeforePublication() throws IOException {
        when(staff.getCurrentStaff().getRole().getName()).thenReturn("OWNER_ADMIN");
        var error =
                assertThrows(
                        ResponseStatusException.class, () -> controller.publish(upload(png(513))));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(jdbc);
    }

    @Test
    void publishesAllFourSizesAtomically() throws IOException {
        when(staff.getCurrentStaff().getRole().getName()).thenReturn("OWNER_ADMIN");
        UUID revision = controller.publish(upload(png(512))).revision();
        var small = ArgumentCaptor.forClass(byte[].class);
        var large = ArgumentCaptor.forClass(byte[].class);
        var maskable = ArgumentCaptor.forClass(byte[].class);
        var apple = ArgumentCaptor.forClass(byte[].class);
        verify(jdbc)
                .update(
                        anyString(),
                        eq(revision),
                        small.capture(),
                        large.capture(),
                        maskable.capture(),
                        apple.capture());
        assertEquals(192, decode(small.getValue()).getWidth());
        assertEquals(512, decode(large.getValue()).getWidth());
        assertEquals(512, decode(maskable.getValue()).getWidth());
        assertEquals(180, decode(apple.getValue()).getWidth());
        assertEquals(0xffffffff, decode(maskable.getValue()).getRGB(0, 0));
    }

    private static MockMultipartFile upload(byte[] bytes) {
        return new MockMultipartFile("file", "icon.png", "image/png", bytes);
    }

    private static byte[] png(int side) throws IOException {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }

    private static BufferedImage decode(byte[] png) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(png));
    }
}
