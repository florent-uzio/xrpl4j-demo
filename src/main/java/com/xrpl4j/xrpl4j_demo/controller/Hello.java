package com.xrpl4j.xrpl4j_demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Hello {

    @GetMapping("/")
    public String hello() {
        return "Hello, XRPL World!";
    }
}