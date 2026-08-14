.PHONY: build klarinet demo test test-klarinet test-android test-ios test-all clean publish

build:
	./gradlew build

klarinet:
	./gradlew :klarinet:build

demo:
	./gradlew :demo-android:assembleDebug

test: test-klarinet

test-klarinet:
	./gradlew :klarinet:allTests

test-android:
	./gradlew :klarinet:connectedAndroidDeviceTest

test-ios:
	./gradlew :klarinet:iosSimulatorArm64Test

test-all:
	./gradlew :klarinet:allTests :klarinet:iosSimulatorArm64Test

clean:
	./gradlew clean

publish:
	./gradlew publishAllPublicationsToMavenCentralRepository
