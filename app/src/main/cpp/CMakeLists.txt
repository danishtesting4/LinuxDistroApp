cmake_minimum_required(VERSION 3.22.1)
project(linux_distro_runtime C ASM)

if(NOT PROOT_SOURCE_DIR OR NOT TALLOC_SOURCE_DIR OR NOT PROOT_JNI_OUTPUT_DIR)
    message(FATAL_ERROR "Pinned PRoot, talloc, and jni output directories are required")
endif()

file(TO_CMAKE_PATH "${PROOT_SOURCE_DIR}" proot_source_dir)
file(TO_CMAKE_PATH "${TALLOC_SOURCE_DIR}" talloc_source_dir)
file(TO_CMAKE_PATH "${PROOT_JNI_OUTPUT_DIR}" proot_jni_output_dir)

file(READ "${proot_source_dir}/arch.h" proot_arch)
string(REPLACE "#define HAS_LOADER_32BIT true" "" proot_arch "${proot_arch}")
file(MAKE_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/proot-source")
file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/proot-source/arch.h" "${proot_arch}")

file(MAKE_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/proot-generated")
file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/proot-generated/build.h"
    "#ifndef BUILD_H\n#define BUILD_H\n"
    "#define VERSION \"termux-5.1.107.102\"\n"
    "#define HAVE_PROCESS_VM\n#define HAVE_SECCOMP_FILTER\n"
    "#endif\n")

add_library(talloc STATIC "${talloc_source_dir}/talloc.c")
target_include_directories(talloc PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/shims"
    "${talloc_source_dir}")
target_compile_definitions(talloc PRIVATE
    "__STDC_WANT_LIB_EXT1__=1"
    "TALLOC_BUILD_VERSION_MAJOR=2"
    "TALLOC_BUILD_VERSION_MINOR=5"
    "TALLOC_BUILD_VERSION_RELEASE=0")
target_compile_options(talloc PRIVATE -O2)

set(proot_sources
    cli/cli.c
    cli/proot.c
    cli/note.c
    execve/enter.c
    execve/exit.c
    execve/shebang.c
    execve/elf.c
    execve/ldso.c
    execve/auxv.c
    execve/aoxp.c
    path/binding.c
    path/glue.c
    path/canon.c
    path/f2fs-bug.c
    path/path.c
    path/proc.c
    path/temp.c
    syscall/seccomp.c
    syscall/syscall.c
    syscall/chain.c
    syscall/enter.c
    syscall/exit.c
    syscall/sysnum.c
    syscall/socket.c
    syscall/heap.c
    syscall/rlimit.c
    syscall/pipe_shadow.c
    tracee/tracee.c
    tracee/mem.c
    tracee/reg.c
    tracee/event.c
    tracee/seccomp.c
    tracee/statx.c
    ptrace/ptrace.c
    ptrace/user.c
    ptrace/wait.c
    extension/extension.c
    extension/ashmem_memfd/ashmem_memfd.c
    extension/kompat/kompat.c
    extension/fake_id0/chown.c
    extension/fake_id0/chroot.c
    extension/fake_id0/getsockopt.c
    extension/fake_id0/sendmsg.c
    extension/fake_id0/socket.c
    extension/fake_id0/open.c
    extension/fake_id0/unlink.c
    extension/fake_id0/rename.c
    extension/fake_id0/chmod.c
    extension/fake_id0/utimensat.c
    extension/fake_id0/access.c
    extension/fake_id0/exec.c
    extension/fake_id0/link.c
    extension/fake_id0/symlink.c
    extension/fake_id0/mk.c
    extension/fake_id0/stat.c
    extension/fake_id0/helper_functions.c
    extension/fake_id0/fake_id0.c
    extension/hidden_files/hidden_files.c
    extension/mountinfo/mountinfo.c
    extension/port_switch/port_switch.c
    extension/sysvipc/sysvipc.c
    extension/sysvipc/sysvipc_msg.c
    extension/sysvipc/sysvipc_sem.c
    extension/sysvipc/sysvipc_shm.c
    extension/link2symlink/link2symlink.c
    extension/fix_symlink_size/fix_symlink_size.c)
list(TRANSFORM proot_sources PREPEND "${proot_source_dir}/")
set_source_files_properties(
    "${proot_source_dir}/extension/ashmem_memfd/ashmem_memfd.c"
    PROPERTIES COMPILE_OPTIONS "-include;string.h")

if(ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "x86_64")
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(proot_loader_address "0x5400000000")
    else()
        set(proot_loader_address "0x600000000000")
    endif()

    add_executable(proot_loader
        "${proot_source_dir}/loader/loader.c"
        "${proot_source_dir}/loader/assembly.S")
    target_include_directories(proot_loader PRIVATE
        "${CMAKE_CURRENT_BINARY_DIR}/proot-source"
        "${CMAKE_CURRENT_BINARY_DIR}/proot-generated"
        "${proot_source_dir}")
    target_compile_definitions(proot_loader PRIVATE
        _FILE_OFFSET_BITS=64 _GNU_SOURCE ARG_MAX=131072)
    target_compile_options(proot_loader PRIVATE -O2 -fPIC -ffreestanding)
    target_link_options(proot_loader PRIVATE
        -static -nostdlib
        "LINKER:--build-id=none,-Ttext=${proot_loader_address},-z,noexecstack,--no-gc-sections"
        "LINKER:--rosegment")
    set_target_properties(proot_loader PROPERTIES
        PREFIX "lib" OUTPUT_NAME "proot_loader" SUFFIX ".so")

    if(ANDROID_ABI STREQUAL "arm64-v8a")
        find_program(PROOT_READELF NAMES llvm-readelf
            HINTS "${ANDROID_TOOLCHAIN_ROOT}/bin" REQUIRED)
        add_custom_command(
            OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/proot-generated/loader-info.c"
            COMMAND "${CMAKE_COMMAND}"
                "-DREADELF=${PROOT_READELF}"
                "-DLOADER=$<TARGET_FILE:proot_loader>"
                "-DOUTPUT=${CMAKE_CURRENT_BINARY_DIR}/proot-generated/loader-info.c"
                -P "${CMAKE_CURRENT_SOURCE_DIR}/GenerateLoaderInfo.cmake"
            DEPENDS proot_loader
            VERBATIM)
        list(APPEND proot_sources "${CMAKE_CURRENT_BINARY_DIR}/proot-generated/loader-info.c")
    endif()
endif()

add_executable(proot ${proot_sources})
target_include_directories(proot PRIVATE
    "${CMAKE_CURRENT_BINARY_DIR}/proot-source"
    "${CMAKE_CURRENT_BINARY_DIR}/proot-generated"
    "${talloc_source_dir}"
    "${proot_source_dir}")
target_compile_definitions(proot PRIVATE
    _FILE_OFFSET_BITS=64
    _GNU_SOURCE
    ARG_MAX=131072
    "PROOT_UNBUNDLE_LOADER=\"/data/user/0/com.danis.linuxdistroapp/files/libexec/proot\"")
target_compile_options(proot PRIVATE
    -O2 -Wall -Wextra -Wno-unused-parameter -Wno-sign-compare
    -Wno-error=implicit-function-declaration)
target_link_options(proot PRIVATE "LINKER:-z,noexecstack")
target_link_libraries(proot PRIVATE talloc)
set_target_properties(proot PROPERTIES
    PREFIX "lib" OUTPUT_NAME "proot" SUFFIX ".so")

add_custom_command(TARGET proot POST_BUILD
    COMMAND "${CMAKE_COMMAND}" -E make_directory
        "${proot_jni_output_dir}/${ANDROID_ABI}"
    COMMAND "${CMAKE_COMMAND}" -E copy "$<TARGET_FILE:proot>"
        "${proot_jni_output_dir}/${ANDROID_ABI}/libproot.so"
    VERBATIM)

if(ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "x86_64")
    add_custom_command(TARGET proot_loader POST_BUILD
        COMMAND "${CMAKE_COMMAND}" -E make_directory
            "${proot_jni_output_dir}/${ANDROID_ABI}"
        COMMAND "${CMAKE_COMMAND}" -E copy "$<TARGET_FILE:proot_loader>"
            "${proot_jni_output_dir}/${ANDROID_ABI}/libproot_loader.so"
        VERBATIM)
endif()
