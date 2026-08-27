ANDROID_HOME ?= $(firstword $(wildcard $(HOME)/Android/Sdk /opt/android-sdk $(HOME)/Library/Android/sdk))
ANDROID_NDK_HOME ?= $(lastword $(sort $(wildcard $(ANDROID_HOME)/ndk/*)))
JAVA_HOME ?= $(firstword $(wildcard \
    /usr/lib/jvm/java-17-openjdk \
    /usr/lib/jvm/java-17-openjdk-amd64 \
    /usr/lib/jvm/default-runtime \
    $(shell /usr/libexec/java_home -v 17 2>/dev/null) \
))
export JAVA_HOME
AAR_OUT := app/libs/udpfsbridge.aar

.PHONY: all aar apk apk-debug apk-release install test clean check-env

check-env:
	@if [ -z "$(ANDROID_HOME)" ]; then echo "Error: ANDROID_HOME not found. Export it in your environment."; exit 1; fi
	@if [ -z "$(ANDROID_NDK_HOME)" ]; then echo "Error: NDK not found under $(ANDROID_HOME)/ndk."; exit 1; fi
	@if [ -z "$(JAVA_HOME)" ]; then echo "Error: JDK 17 not found. Export JAVA_HOME."; exit 1; fi

all: apk-debug

test:
	go test -tags nochd ./udpfsdbridge/ -count=1

aar:
	@mkdir -p app/libs
	ANDROID_HOME=$(ANDROID_HOME) ANDROID_NDK_HOME=$(ANDROID_NDK_HOME) \
	gomobile bind -tags nochd -target=android -androidapi 28 -javapkg=com.udpfs -o $(AAR_OUT) ./udpfsdbridge

apk-debug: aar
	./gradlew assembleDebug

apk-release: aar
	./gradlew assembleRelease

apk: apk-debug apk-release

install: apk-debug
	adb install -r app/build/outputs/apk/debug/app-debug.apk

clean:
	rm -f $(AAR_OUT) app/libs/udpfsbridge-sources.jar app/libs/*.jar
	./gradlew clean
