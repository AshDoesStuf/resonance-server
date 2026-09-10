package com.ash.resonance.upload;

import com.ash.resonance.auth.AuthenticatedPrincipal;
import com.ash.resonance.upload.dto.CompleteUploadRequest;
import com.ash.resonance.upload.dto.CreateUploadRequest;
import com.ash.resonance.upload.dto.CreateUploadResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping
    public CreateUploadResponse create(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                        @Valid @RequestBody CreateUploadRequest request) {
        return uploadService.create(principal.userId(), principal.deviceId(), request);
    }

    @PutMapping(value = "/{id}/chunks/{n}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Void> putChunk(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                          @PathVariable("id") UUID uploadId,
                                          @PathVariable("n") int chunkNumber,
                                          @RequestBody byte[] chunk) {
        uploadService.writeChunk(principal.userId(), uploadId, chunkNumber, chunk);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/complete")
    public CompleteUploadRequest.Response complete(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                     @PathVariable("id") UUID uploadId,
                                                     @Valid @RequestBody CompleteUploadRequest request) {
        return uploadService.complete(principal.userId(), uploadId, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> abort(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                       @PathVariable("id") UUID uploadId) {
        uploadService.abort(principal.userId(), uploadId);
        return ResponseEntity.noContent().build();
    }
}
