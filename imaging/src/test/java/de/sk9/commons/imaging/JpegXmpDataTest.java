package de.sk9.commons.imaging;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JpegXmpDataTest {
	@ParameterizedTest
	@MethodSource("provideDataForReadKeywords")
	void testReadKeywords(String resourceName, String... expectedKeywords) throws Exception {
		ClassLoader classLoader = getClass().getClassLoader();
		File file = new File(classLoader.getResource(resourceName).getFile());
		JpegXmpData xmpData = JpegXmpData.fromFile(file);

		assertThat(xmpData.getKeywords(), Matchers.containsInAnyOrder(expectedKeywords));
	}

	private static Stream<Arguments> provideDataForReadKeywords() {
		return Stream.of(
				Arguments.of("no_xmp.jpg", new String[] {}),
				Arguments.of("adobe_lightroom.jpg", new String[] { "Adobe", "Lightroom" }),
				Arguments.of("affinity_photo.jpg", new String[] { "Affinity", "Photo" }),
				Arguments.of("synology_photos.jpg", new String[] { "Synology", "Photos" }),
				Arguments.of("gimp_210.jpg", new String[] { "type=\"Bag\" Gimp, 2.10" }));
	}

	@ParameterizedTest
	@MethodSource("provideDataForWriteKeywords")
	void testWriteKeywords(String resourceName, String... expectedKeywords) throws Exception {
		ClassLoader classLoader = getClass().getClassLoader();
		File templateFile = new File(classLoader.getResource(resourceName).getFile());
		File testFile = File.createTempFile("JpegXmpDataTest", "jpg");
		testFile.deleteOnExit();
		Files.copy(templateFile.toPath(), testFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

		JpegXmpData xmpData = JpegXmpData.fromFile(testFile);

		xmpData.addKeywords(new String[] { "Foo", "Photo" });

		xmpData.replaceInFile(testFile);

		JpegXmpData xmpDataNew = JpegXmpData.fromFile(testFile);

		assertThat(xmpDataNew.getKeywords(), Matchers.containsInAnyOrder(expectedKeywords));

	}

	private static Stream<Arguments> provideDataForWriteKeywords() {
		return Stream.of(
				Arguments.of("no_xmp.jpg", new String[] { "Foo", "Photo" }),
				Arguments.of("adobe_lightroom.jpg", new String[] { "Adobe", "Lightroom", "Foo", "Photo" }),
				Arguments.of("affinity_photo.jpg", new String[] { "Affinity", "Photo", "Foo" }),
				Arguments.of("synology_photos.jpg", new String[] { "Synology", "Photos", "Foo", "Photo" }),
				Arguments.of("gimp_210.jpg", new String[] { "type=\"Bag\" Gimp, 2.10", "Foo", "Photo" }));
	}

	@Test
	void testRemoveKeywordsWithDuplicates() throws JpegXmpDataException {
		JpegXmpData xmpData = new JpegXmpData();

		xmpData.addKeywords("Foo", "Bar", "Foo", "Baz");
		xmpData.removeKeywords("Foo");

		assertThat(xmpData.getKeywords(), Matchers.containsInAnyOrder("Bar", "Baz"));
	}

	@ParameterizedTest
	@MethodSource("provideDataForRemoveKeywords")
	void testRemoveKeywords(String resourceName, String... expectedKeywords) throws Exception {
		ClassLoader classLoader = getClass().getClassLoader();
		File testFile = new File(classLoader.getResource(resourceName).getFile());

		JpegXmpData xmpData = JpegXmpData.fromFile(testFile);

		xmpData.removeKeywords(new String[] { "Foo", "Photo" });

		assertThat(xmpData.getKeywords(), Matchers.containsInAnyOrder(expectedKeywords));
	}

	private static Stream<Arguments> provideDataForRemoveKeywords() {
		return Stream.of(
				Arguments.of("no_xmp.jpg", new String[] {}),
				Arguments.of("adobe_lightroom.jpg", new String[] { "Adobe", "Lightroom" }),
				Arguments.of("affinity_photo.jpg", new String[] { "Affinity" }),
				Arguments.of("synology_photos.jpg", new String[] { "Synology", "Photos" }),
				Arguments.of("gimp_210.jpg", new String[] { "type=\"Bag\" Gimp, 2.10" }));
	}

	@ParameterizedTest
	@MethodSource("provideResourceList")
	void testRemoveAllKeywords(String resourceName) throws Exception {
		ClassLoader classLoader = getClass().getClassLoader();
		File testFile = new File(classLoader.getResource(resourceName).getFile());

		JpegXmpData xmpData = JpegXmpData.fromFile(testFile);

		xmpData.removeAllKeywords();

		assertThat(xmpData.getKeywords(), Matchers.is(empty()));
	}

	private static Stream<Arguments> provideResourceList() {
		return Stream.of(
				Arguments.of("no_xmp.jpg"),
				Arguments.of("adobe_lightroom.jpg"),
				Arguments.of("affinity_photo.jpg"),
				Arguments.of("synology_photos.jpg"),
				Arguments.of("gimp_210.jpg"));
	}

	@ParameterizedTest
	@MethodSource("provideResourceList")
	void testToString(String resourceName) throws JpegXmpDataException {
		ClassLoader classLoader = getClass().getClassLoader();
		File testFile = new File(classLoader.getResource(resourceName).getFile());

		JpegXmpData xmpData = JpegXmpData.fromFile(testFile);

		assertThat(xmpData.toString(), Matchers.anyOf(
				Matchers.startsWith("xmpMeta not initialized"),
				Matchers.startsWith("<?xpacket begin")));
	}

	@ParameterizedTest
	@MethodSource("provideDataForGetStarRating")
	void testStarRating(String resourceName, Integer expectedValue) throws JpegXmpDataException {
		ClassLoader classLoader = getClass().getClassLoader();
		File testFile = new File(classLoader.getResource(resourceName).getFile());

		JpegXmpData xmpData = JpegXmpData.fromFile(testFile);

		assertThat(xmpData.getImageRating(), Matchers.is(expectedValue));
	}

	private static Stream<Arguments> provideDataForGetStarRating() {
		return Stream.of(
				Arguments.of("no_xmp.jpg", null),
				Arguments.of("adobe_lightroom.jpg", 3),
				Arguments.of("affinity_photo.jpg", 3),
				Arguments.of("synology_photos.jpg", 3),
				Arguments.of("gimp_210.jpg", 3));
	}

}
