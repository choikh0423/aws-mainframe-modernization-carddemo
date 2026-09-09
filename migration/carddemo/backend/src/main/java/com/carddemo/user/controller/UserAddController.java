package com.carddemo.user.controller;

import com.carddemo.user.dto.UserActionResponse;
import com.carddemo.user.dto.UserAddRequest;
import com.carddemo.user.service.UserAddService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CU01 — Add User (COUSR01C): {@code POST /api/admin/users}.
 * 201 with the green "User &lt;id&gt; has been added ..." on a successful WRITE;
 * 400 with the first failing edit's literal; 409 "User ID already exist...".
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserAddController {

    private final UserAddService service;

    public UserAddController(UserAddService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<UserActionResponse> add(@RequestBody UserAddRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.add(request));
    }
}
