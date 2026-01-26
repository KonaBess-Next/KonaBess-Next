
cmake_minimum_required(VERSION 3.10.2)

project("konadts")

# Define where the DTC source code is located
set(DTC_DIR ${CMAKE_SOURCE_DIR}/dtc)
set(LIBFDT_DIR ${CMAKE_SOURCE_DIR}/dtc/libfdt)

# Include directories
include_directories(
    ${DTC_DIR}
    ${LIBFDT_DIR}
)

# Source files for libfdt
set(LIBFDT_SOURCES
    ${LIBFDT_DIR}/fdt.c
    ${LIBFDT_DIR}/fdt_ro.c
    ${LIBFDT_DIR}/fdt_wip.c
    ${LIBFDT_DIR}/fdt_sw.c
    ${LIBFDT_DIR}/fdt_rw.c
    ${LIBFDT_DIR}/fdt_strerror.c
    ${LIBFDT_DIR}/fdt_empty_tree.c
    ${LIBFDT_DIR}/fdt_addresses.c
)

# Source files for DTC
# Note: lexer.lex.c and parser.tab.c are assumed to be pre-generated or part of the source tree
set(DTC_SOURCES
    ${DTC_DIR}/dtc.c
    ${DTC_DIR}/flattree.c
    ${DTC_DIR}/fstree.c
    ${DTC_DIR}/data.c
    ${DTC_DIR}/livetree.c
    ${DTC_DIR}/treesource.c
    ${DTC_DIR}/srcpos.c
    ${DTC_DIR}/checks.c
    ${DTC_DIR}/util.c
    # Add generated files if they exist, or user must generate them:
    ${DTC_DIR}/dtc-lexer.lex.c
    ${DTC_DIR}/dtc-parser.tab.c
)

# Native wrapper source
set(NATIVE_WRAPPER_SOURCES
    native-lib.cpp
)

# Define the shared library
add_library(
    konadts
    SHARED
    ${NATIVE_WRAPPER_SOURCES}
    ${DTC_SOURCES}
    ${LIBFDT_SOURCES}
)

# Compilation flags
target_compile_definitions(konadts PRIVATE NO_YAML)
set_property(TARGET konadts PROPERTY C_STANDARD 99)
target_compile_options(konadts PRIVATE -Wno-unused-parameter -Wno-sign-compare)

# Link libraries
find_library(
    log-lib
    log
)

target_link_libraries(
    konadts
    ${log-lib}
)
