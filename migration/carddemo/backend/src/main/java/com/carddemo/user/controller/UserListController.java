package com.carddemo.user.controller;

import com.carddemo.user.dto.UserListResponse;
import com.carddemo.user.service.UserListService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CU00 — List Users (COUSR00C). The paged USRSEC browse the legacy screen ran on
 * ENTER / PF7 / PF8:
 * <pre>
 *   GET /api/admin/users                          -> first 10 by User ID (FR-UL-1)
 *   GET /api/admin/users?startId=&lt;id&gt;             -> from that id, inclusive (FR-UL-2)
 *   GET /api/admin/users?startId=&lt;id&gt;&amp;dir=next    -> next 10, PF8 (FR-UL-3)
 *   GET /api/admin/users?startId=&lt;id&gt;&amp;dir=prev    -> prev 10, PF7 (FR-UL-4)
 * </pre>
 * The {@code /api/admin/**} prefix is the COADM01C boundary the shared
 * {@code SecurityConfig} restricts to ROLE_ADMIN (FR-US-1).
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserListController {

    private final UserListService service;

    public UserListController(UserListService service) {
        this.service = service;
    }

    @GetMapping
    public UserListResponse list(
            @RequestParam(name = "startId", required = false) String startId,
            @RequestParam(name = "dir", required = false) String dir) {
        return service.list(startId, dir);
    }
}
