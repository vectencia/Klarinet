.PHONY: build klarinet demo test test-klarinet test-dsp test-android test-ios test-all clean publish

build:
	./gradlew build

klarinet:
	./gradlew :klarinet:build

demo:
	./gradlew :demo-android:assembleDebug

test: test-klarinet

test-klarinet:
	./gradlew :klarinet:allTests

test-dsp:
	./gradlew :klarinet:dspTests

test-android:
	./gradlew :klarinet:connectedAndroidDeviceTest

test-ios:
	./gradlew :klarinet:iosSimulatorArm64Test

test-all:
	./gradlew :klarinet:allTests :klarinet:dspTests :klarinet:iosSimulatorArm64Test

clean:
	./gradlew clean

publish:
	./gradlew publishAllPublicationsToMavenCentralRepository
