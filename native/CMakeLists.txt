cmake_minimum_required(VERSION 3.16.3)

project(bootstrap)

find_package(JNI REQUIRED)

add_library(bootstrap SHARED bootstrap.c)
target_include_directories(bootstrap PRIVATE ${JNI_INCLUDE_DIRS})
