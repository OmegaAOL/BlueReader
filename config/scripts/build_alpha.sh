#!/bin/bash

source ~/.bashrc

function main() {

set -o pipefail
set -e
set -x

cd ~/build

STATE_DIR="$(pwd)/state/"
BUILD_NUMBER="$(echo $(<$STATE_DIR/last_build_number)+1 | bc)"

echo "Build number $BUILD_NUMBER"

cd BlueReader

git reset --hard
git clean -xdf

git fetch
git checkout master
git rebase

LAST_COMMIT_BUILT="$(<$STATE_DIR/last_commit_built)"

echo "Last built commit $LAST_COMMIT_BUILT"

CURRENT_COMMIT="$(git rev-parse HEAD)"

echo "Current commit $CURRENT_COMMIT"

if [ "$LAST_COMMIT_BUILT" == "$CURRENT_COMMIT" ]; then
	echo "Not building again."
	exit 0
fi

echo "Changing package..."
git mv src/main/java/org/quantumbadger/redreader src/main/java/org/quantumbadger/redreaderalpha
find . -type f -not -path "*/.git/*" -not -path "*/config/scripts/*" -not -name "README.md" -exec sed -i 's/org\.quantumbadger\.redreader/org.omegaaol.bluereaderalpha/g' {} \;
find . -type f -not -path "*/.git/*" -not -path "*/config/scripts/*" -exec sed -i 's/org\/quantumbadger\/redreader/org\/quantumbadger\/redreaderalpha/g' {} \;

echo "Changing icon..."
sed -i 's/@drawable\/icon/@drawable\/icon_inv/g' src/main/AndroidManifest.xml
sed -i 's/@mipmap\/icon/@mipmap\/icon_inv/g' src/main/AndroidManifest.xml

echo "Changing name..."
find src/main/res -name "strings.xml" -exec sed -i 's/BlueReader/BlueReader Alpha '"$BUILD_NUMBER"'/g' {} \;
sed -i 's/versionName = ".*/versionName = "Alpha '"${BUILD_NUMBER}"'"/g' build.gradle.kts
sed -i 's/versionCode .*/versionCode = '"$((${BUILD_NUMBER} + 10000))"'/g' build.gradle.kts

echo "Building..."

./gradlew packageRelease

echo "Signing..."

zipalign -v 4 build/outputs/apk/release/BlueReader-release-unsigned.apk BlueReader_Alpha_${BUILD_NUMBER}.apk
~/android-sdk-linux/build-tools/30.0.3/apksigner sign --ks ../key/rralpha.keystore --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true -v --ks-pass env:RR_KS_PASS --key-pass env:RR_KEY_PASS BlueReader_Alpha_${BUILD_NUMBER}.apk

echo "Writing build info..."

echo $BUILD_NUMBER > BlueReader_Alpha_${BUILD_NUMBER}.txt
echo $CURRENT_COMMIT >> BlueReader_Alpha_${BUILD_NUMBER}.txt
date >> BlueReader_Alpha_${BUILD_NUMBER}.txt
echo $(sha256sum BlueReader_Alpha_${BUILD_NUMBER}.apk | head -c 64) >> BlueReader_Alpha_${BUILD_NUMBER}.txt
echo "BlueReader_Alpha_${BUILD_NUMBER}.apk" >> BlueReader_Alpha_${BUILD_NUMBER}.txt

echo "Uploading..."

cat BlueReader_Alpha_${BUILD_NUMBER}.apk | pv | ssh user@localhost -p 1234 "cat > /var/www/html/alpha/builds/BlueReader_Alpha_${BUILD_NUMBER}.apk"
cat BlueReader_Alpha_${BUILD_NUMBER}.txt | pv | ssh user@localhost -p 1234 "cat > /var/www/html/alpha/builds/BlueReader_Alpha_${BUILD_NUMBER}.txt"

echo "Updating f-droid..."

cd ~/build
./update_fdroid.sh

echo $BUILD_NUMBER > $STATE_DIR/last_build_number
echo $CURRENT_COMMIT > $STATE_DIR/last_commit_built

echo "Done!"
}

(
if flock -x -n 200
then
    main
else
    echo "Failed to acquire lock."
    exit 1
fi

) 200>/var/lock/rr_build.lock

