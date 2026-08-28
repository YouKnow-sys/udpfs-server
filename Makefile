ANDROID_HOME ?= $(firstword $(wildcard $(HOME)/Android/Sdk /opt/android-sdk $(HOME)/Library/Android/sdk))
ANDROID_NDK_HOME ?= $(lastword $(sort $(wildcard $(ANDROID_HOME)/ndk/*)))

JAVA_HOME ?= $(firstword $(wildcard \
    /usr/lib/jvm/java-17-openjdk \
    /usr/lib/jvm/java-17-openjdk-amd64 \
    /usr/lib/jvm/default-runtime \
    $(shell /usr/libexec/java_home -v 17 2>/dev/null) \
))
export JAVA_HOME

AAR_OUT         := app/libs/udpfsbridge.aar
DEBUG_APK_DIR   := app/build/outputs/apk/debug
RELEASE_APK_DIR := app/build/outputs/apk/release

.DELETE_ON_ERROR:
.PHONY: all aar apk apk-debug apk-release apk-abi install test check clean check-env

all: apk-debug

check-env:
	@if [ -z "$(ANDROID_HOME)" ]; then echo "Error: ANDROID_HOME not found. Export it in your environment."; exit 1; fi
	@if [ -z "$(ANDROID_NDK_HOME)" ]; then echo "Error: NDK not found under $(ANDROID_HOME)/ndk."; exit 1; fi
	@if [ -z "$(JAVA_HOME)" ]; then echo "Error: JDK 17 not found. Export JAVA_HOME."; exit 1; fi

test:
	go test -tags nochd ./udpfsdbridge/ -count=1

check:
	go vet -tags nochd ./udpfsdbridge/
	$(MAKE) test

aar: check-env
	@mkdir -p app/libs
	ANDROID_HOME="$(ANDROID_HOME)" ANDROID_NDK_HOME="$(ANDROID_NDK_HOME)" \
	gomobile bind -tags nochd -target=android -androidapi 28 -javapkg=com.udpfs \
		-ldflags="-s -w" -o "$(AAR_OUT)" ./udpfsdbridge

apk-debug: aar
	./gradlew assembleDebug

apk-release: aar
	./gradlew assembleRelease

apk: apk-debug apk-release

apk-abi: aar
	./gradlew assembleRelease -PabiSplits=true
	@ls -la $(RELEASE_APK_DIR)/app-*-release.apk

install: apk-debug
	adb install -r "$(DEBUG_APK_DIR)/app-debug.apk"

clean:
	rm -f "$(AAR_OUT)" app/libs/udpfsbridge-sources.jar app/libs/*.jar
	rm -rf build
	./gradlew clean
