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

    private final MenuImportJobs jobs;

    @GetMapping("/import/jobs/{jobId}")
    public MenuImportJobs.Job job(@PathVariable long branchId,@PathVariable java.util.UUID jobId) {return jobs.get(branchId,jobId);}

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
    public ResponseEntity<?>
    validateImport(

            @PathVariable
            Long branchId,

            @RequestPart("file")
            MultipartFile file
    ) {

        if(jobs.enabled())return ResponseEntity.accepted().body(jobs.enqueue(branchId,file,"VALIDATE"));
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
    public ResponseEntity<?>
    importMenu(

            @PathVariable
            Long branchId,

            @RequestPart("file")
            MultipartFile file
    ) {

        if(jobs.enabled())return ResponseEntity.accepted().body(jobs.enqueue(branchId,file,"IMPORT"));
        return ResponseEntity.ok(
                menuImportService
                        .importMenu(
                                branchId,
                                file
                        )
        );
    }
}
