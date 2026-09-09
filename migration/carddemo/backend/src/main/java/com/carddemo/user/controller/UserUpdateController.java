package com.carddemo.user.controller;

import com.carddemo.user.dto.UserActionResponse;
import com.carddemo.user.dto.UserDetailResponse;
import com.carddemo.user.dto.UserUpdateRequest;
import com.carddemo.user.service.UserUpdateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CU02 — Update User (COUSR02C):
 * <pre>
 *   GET /api/admin/users/{userId} -> ENTER fetch (FR-UU-2..FR-UU-5); CU03 uses
 *                                    the same read (FR-UD-2..FR-UD-5)
 *   PUT /api/admin/users/{userId} -> PF5/PF3 save (FR-UU-6..FR-UU-10)
 * </pre>
 * The key travels in the path because CU02 cannot change it (FR-UU-15). A blank
 * key reaches the empty-key edit and comes back as 400
 * "User ID can NOT be empty...", exactly as the legacy screen reports it.
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserUpdateController {

    private final UserUpdateService service;

    public UserUpdateController(UserUpdateService service) {
        this.service = service;
    }

    @GetMapping("/{userId}")
    public UserDetailResponse fetch(@PathVariable("userId") String userId) {
        return service.fetch(userId);
    }

    @PutMapping("/{userId}")
    public UserActionResponse update(@PathVariable("userId") String userId,
                                     @RequestBody UserUpdateRequest request) {
        return service.update(userId, request);
    }
}
