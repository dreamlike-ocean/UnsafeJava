package io.github.dreamlike.jit;

public record Syscall(int error, int ret) { }