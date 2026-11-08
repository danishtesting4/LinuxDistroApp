execute_process(
    COMMAND "${READELF}" -s "${LOADER}"
    OUTPUT_VARIABLE symbols
    RESULT_VARIABLE status
)
if(NOT status EQUAL 0)
    message(FATAL_ERROR "llvm-readelf failed for ${LOADER}")
endif()
string(REPLACE "\r" "" symbols "${symbols}")

function(symbol_address name out)
    string(REGEX MATCH "[0-9]+: ([0-9a-fA-F]+) [^\n]* ${name}\n" match "${symbols}\n")
    if(NOT match)
        message(FATAL_ERROR "Symbol ${name} not found in ${LOADER}")
    endif()
    math(EXPR value "0x${CMAKE_MATCH_1}" OUTPUT_FORMAT DECIMAL)
    set(${out} ${value} PARENT_SCOPE)
endfunction()

symbol_address(pokedata_workaround workaround)
symbol_address(_start start)
math(EXPR offset "${workaround} - ${start}")
file(WRITE "${OUTPUT}" "#include <unistd.h>\nconst ssize_t offset_to_pokedata_workaround=${offset};\n")
