package com.salvador.bmxbuilder.web;

/** JSON body for every error response: {"error": "..."} */
public record ApiError(String error) {
}
