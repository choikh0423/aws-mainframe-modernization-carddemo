package com.carddemo.user.controller;

import com.carddemo.user.dto.UserActionResponse;
import com.carddemo.user.service.UserDeleteService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CU03 — Delete User (COUSR03C): {@code DELETE /api/admin/users/{userId}}, the
 * PF5 path. 200 with the green "User &lt;id&gt; has been deleted ...";
 * 404 "User ID NOT found..."; 400 "User ID can NOT be empty..." for a blank key.
 * The screen's ENTER fetch reuses {@code GET /api/admin/users/{userId}}.
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserDeleteController {

    private final UserDeleteService service;

    public UserDeleteController(UserDeleteService service) {
        this.service = service;
    }

    @DeleteMapping("/{userId}")
    public UserActionResponse delete(@PathVariable("userId") String userId) {
        return service.delete(userId);
    }
}
