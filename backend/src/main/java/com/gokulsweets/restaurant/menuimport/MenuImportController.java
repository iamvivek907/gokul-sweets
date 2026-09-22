package com.gokulsweets.restaurant.menuimport;

import com.gokulsweets.restaurant.menuimport.dto.MenuImportResultResponse;
import com.gokulsweets.restaurant.menuimport.dto.MenuImportValidationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(
        "/api/admin/branches/{branchId}/menu"
)
@RequiredArgsConstructor
public class MenuImportController {

    private final MenuImportService
            menuImportService;

    private final MenuTemplateService
            menuTemplateService;


    @GetMapping("/import/template")
    public ResponseEntity<byte[]> downloadTemplate(
            @PathVariable
            Long branchId
    ) {

        byte[] content =
                menuTemplateService
                        .createTemplate(
                                branchId
                        );

        return ResponseEntity
                .ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"gokul-menu-template.xlsx\""
                )
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        )
                )
                .body(
                        content
                );
    }


    @PostMapping(
            value = "/import/validate",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<MenuImportValidationResponse>
    validateImport(

            @PathVariable
            Long branchId,

            @RequestPart("file")
            MultipartFile file
    ) {

        return ResponseEntity.ok(
                menuImportService
                        .validate(
                                branchId,
                                file
                        )
        );
    }


    @PostMapping(
            value = "/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<MenuImportResultResponse>
    importMenu(

            @PathVariable
            Long branchId,

            @RequestPart("file")
            MultipartFile file
    ) {

        return ResponseEntity.ok(
                menuImportService
                        .importMenu(
                                branchId,
                                file
                        )
        );
    }
}
