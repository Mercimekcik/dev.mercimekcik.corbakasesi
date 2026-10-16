# Injected into Butterscotch's CMake project via CMAKE_PROJECT_butterscotch_INCLUDE (see app/build.gradle.kts),
# so we can add our own sources to the runtime without touching the upstream checkout.
# The target doesn't exist yet when this runs (right after project()), so the real work is deferred to the end of the file.

function(mercimekcik_patch_butterscotch)
    target_sources(butterscotch PRIVATE ${CMAKE_CURRENT_FUNCTION_LIST_DIR}/apk_assets.c)
    target_link_libraries(butterscotch PRIVATE android)
    # Route the runtime's file access through apk_assets.c (reads "/@assets/..." straight from the APK)
    target_link_options(butterscotch PRIVATE "-Wl,--wrap=fopen" "-Wl,--wrap=fopen64" "-Wl,--wrap=stat" "-Wl,--wrap=fileno" "-Wl,--wrap=fstat")
endfunction()

cmake_language(DEFER CALL mercimekcik_patch_butterscotch)
