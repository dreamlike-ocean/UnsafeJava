cmake_minimum_required(VERSION 3.25)
project(virtualThreadUnsafe)

set(CMAKE_CXX_STANDARD 11)

add_library(virtualThreadUnsafe SHARED top_dreamlike_VirtualThreadUnsafe.cpp
        top_dreamlike_VirualThreadUnsafe.h)
