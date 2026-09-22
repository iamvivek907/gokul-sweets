package com.gokulsweets.restaurant.rebate;

import com.gokulsweets.restaurant.rebate.dto.CreateRebateRequest;
import com.gokulsweets.restaurant.rebate.dto.RebateResponse;
import com.gokulsweets.restaurant.rebate.dto.UpdateRebateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/rebates")
@RequiredArgsConstructor
@Slf4j
public class RebateManagementController {

    private final RebateManagementService
            rebateManagementService;

    @PostMapping
    public ResponseEntity<RebateResponse> create(

            @Valid
            @RequestBody
            CreateRebateRequest request
    ) {

        log.debug(
                "Admin rebate creation requested: code={}, scope={}, visibility={}, type={}, branchId={}",
                request.code(),
                request.scope(),
                request.visibility(),
                request.rebateType(),
                request.branchId()
        );

        RebateResponse response =
                rebateManagementService
                        .create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<RebateResponse>>
    getAll() {

        return ResponseEntity.ok(
                rebateManagementService
                        .getAll()
        );
    }

    @GetMapping("/{rebateId}")
    public ResponseEntity<RebateResponse>
    getById(

            @PathVariable
            Long rebateId
    ) {

        return ResponseEntity.ok(
                rebateManagementService
                        .getById(
                                rebateId
                        )
        );
    }

    @PutMapping("/{rebateId}")
    public ResponseEntity<RebateResponse>
    update(

            @PathVariable
            Long rebateId,

            @Valid
            @RequestBody
            UpdateRebateRequest request
    ) {

        return ResponseEntity.ok(
                rebateManagementService
                        .update(
                                rebateId,
                                request
                        )
        );
    }

    @PatchMapping(
            "/{rebateId}/activate"
    )
    public ResponseEntity<RebateResponse>
    activate(

            @PathVariable
            Long rebateId
    ) {

        return ResponseEntity.ok(
                rebateManagementService
                        .activate(
                                rebateId
                        )
        );
    }

    @PatchMapping(
            "/{rebateId}/deactivate"
    )
    public ResponseEntity<RebateResponse>
    deactivate(

            @PathVariable
            Long rebateId
    ) {

        return ResponseEntity.ok(
                rebateManagementService
                        .deactivate(
                                rebateId
                        )
        );
    }

}
