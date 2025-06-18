#!/bin/sh

# Validates any tag starting with "v" matches the version in build.gradle.
# Arguments: local_ref local_sha remote_ref remote_sha are passsed by git on stdin
while read local_ref local_oid remote_ref remote_oid
do
    # Call Gradle task with local_ref
    ./gradlew checkTagMatchesVersion -Plocal_ref="$local_ref"
    if [ $? -ne 0 ]; then
        echo "Tag check failed. Aborting push."
        exit 1
    fi
done

exit 0
