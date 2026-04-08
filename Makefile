.PHONY: build koboe demo test test-koboe test-android test-ios test-all clean publish

build:
	./gradlew build

koboe:
	./gradlew :koboe:build

demo:
	./gradlew :demo:assembleDebug

test: test-koboe

test-koboe:
	./gradlew :koboe:allTests

test-android:
	./gradlew :koboe:connectedDebugAndroidTest

test-ios:
	./gradlew :koboe:iosSimulatorArm64Test

test-all:
	./gradlew :koboe:allTests :koboe:iosSimulatorArm64Test

clean:
	./gradlew clean

publish:
	./gradlew publishAllPublicationsToMavenCentralRepository
