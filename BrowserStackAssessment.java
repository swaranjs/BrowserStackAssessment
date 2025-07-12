
package com.example;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;

import java.io.*;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

public class ElPaisScraper {

    // Translation API (free tier - MyMemory)
    private static final String TRANSLATE_API = "https://api.mymemory.translated.net/get?q=%s&langpair=es|en";

    public static void main(String[] args) throws Exception {
        System.setProperty("webdriver.chrome.driver", "path/to/chromedriver"); // Update this path if needed

        List<String> spanishTitles = new ArrayList<>();
        List<String> englishTitles = new ArrayList<>();

        // Step 1: Open El País and go to Opinion section
        WebDriver driver = new ChromeDriver();
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

        try {
            driver.get("https://elpais.com/");
            WebElement opinionLink = driver.findElement(By.cssSelector("a[data-section='opinion']"));
            opinionLink.click();

            List<WebElement> articles = driver.findElements(By.cssSelector("article"));
            int count = Math.min(5, articles.size());

            for (int i = 0; i < count; i++) {
                WebElement article = articles.get(i);
                String title = article.findElement(By.tagName("h2")).getText();
                String link = article.findElement(By.cssSelector("a")).getAttribute("href");

                spanishTitles.add(title);
                System.out.println("ES Title: " + title);

                // Navigate to the article page to get body and image
                driver.get(link);
                String bodyText = driver.findElement(By.cssSelector("div[class*='article_body']")).getText();
                System.out.println("Body: " + bodyText);

                // Download image if exists
                try {
                    WebElement imgElement = driver.findElement(By.cssSelector("figure img"));
                    String imageUrl = imgElement.getAttribute("src");
                    downloadImage(imageUrl, title);
                } catch (NoSuchElementException e) {
                    System.out.println("No image found for article: " + title);
                }

                driver.navigate().back();
            }

        } catch (Exception e) {
            System.err.println("Error during scraping: " + e.getMessage());
        } finally {
            driver.quit();
        }

        // Step 2: Translate Titles
        for (String title : spanishTitles) {
            String translated = translateTitle(title);
            englishTitles.add(translated);
            System.out.println("EN Title: " + translated);
        }

        // Step 3: Analyze Word Frequency
        Map<String, Integer> frequency = new HashMap<>();
        for (String title : englishTitles) {
            String[] words = title.split("\\W+");
            for (String word : words) {
                if (word.isBlank()) continue;
                word = word.toLowerCase();
                frequency.put(word, frequency.getOrDefault(word, 0) + 1);
            }
        }

        System.out.println("\nWords repeated more than twice:");
        frequency.entrySet().stream()
                .filter(entry -> entry.getValue() > 2)
                .forEach(entry -> System.out.println(entry.getKey() + ": " + entry.getValue()));
    }

    // Helper: Translate Spanish title to English using API
    private static String translateTitle(String text) {
        try {
            String url = String.format(TRANSLATE_API, URLEncoder.encode(text, "UTF-8"));
            String response = EntityUtils.toString(HttpClients.createDefault().execute(new HttpGet(url)).getEntity());
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            return json.getAsJsonObject("responseData").get("translatedText").getAsString();
        } catch (Exception e) {
            System.err.println("Translation failed: " + e.getMessage());
            return text;
        }
    }

    // Helper: Download cover image to local machine
    private static void downloadImage(String imageUrl, String title) {
        try (InputStream in = new URL(imageUrl).openStream()) {
            String fileName = title.replaceAll("[^a-zA-Z0-9]", "_") + ".jpg";
            Path imagePath = Paths.get("images", fileName);
            Files.createDirectories(imagePath.getParent());
            Files.copy(in, imagePath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Image saved: " + imagePath.toString());
        } catch (IOException e) {
            System.err.println("Failed to download image: " + e.getMessage());
        }
    }
}
