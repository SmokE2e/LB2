package org.vasechek;

import org.json.JSONObject;
import java.io.*;
import java.nio.file.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Введите путь к JSON-файлу:");
        String inputFilePath = scanner.nextLine().trim();

        String fileNameWithoutExtension = getFileNameWithoutExtension(inputFilePath);

        List<Review> reviews = loadReviews(inputFilePath);

        if (reviews.isEmpty()) {
            System.out.println("Отзывы не найдены");
            return;
        }

        System.out.println("Введите путь для сохранения CSV-файлов:");
        String outputDirPath = scanner.nextLine().trim();

        Path outputPath = Paths.get(outputDirPath);
        if (!Files.exists(outputPath)) {
            try {
                Files.createDirectories(outputPath);
                System.out.println("Директория создана: " + outputPath.toAbsolutePath());
            } catch (IOException e) {
                System.err.println("Ошибка создания директории: " + e.getMessage());
                return;
            }
        }

        while (true) {
            System.out.println("\nВыберите действие:");
            System.out.println("1. Список продуктов по популярности");
            System.out.println("2. Список продуктов по рейтингу (взвешенный рейтинг)");
            System.out.println("3. Самые популярные товары за период");
            System.out.println("4. Поиск товара по тексту отзыва");
            System.out.println("0. Выход");
            System.out.print("Введите ваш выбор: ");

            int choice = scanner.nextInt();
            scanner.nextLine();

            List<String[]> result = new ArrayList<>();

            switch (choice) {
                case 1 -> {
                    List<String[]> popularityResults = listProductsByPopularity(reviews, scanner);
                    if (!popularityResults.isEmpty()) {
                        saveResultsToCSV(popularityResults, fileNameWithoutExtension, "popular_products.csv", scanner);
                    }
                }
                case 2 -> {
                    List<String[]> ratingResults = listProductsByWeightedRating(reviews, scanner);
                    if (!ratingResults.isEmpty()) {
                        saveResultsToCSV(ratingResults, fileNameWithoutExtension, "weighted_ratings.csv", scanner);
                    }
                }
                case 3 -> {
                    List<String[]> periodResults = listMostPopularProductsInPeriod(reviews, scanner);
                    if (!periodResults.isEmpty()) {
                        saveResultsToCSV(periodResults, fileNameWithoutExtension, "popular_in_period.csv", scanner);
                    }
                }
                case 4 -> {
                    List<String[]> searchResults = searchProductByReviewText(reviews, scanner);
                    if (!searchResults.isEmpty()) {
                        saveResultsToCSV(searchResults, fileNameWithoutExtension, "search_results.csv", scanner);
                    }
                }
                case 0 -> {
                    System.out.println("Выход...");
                    return;
                }
                default -> System.out.println("Неверный выбор. Попробуйте снова");
            }
        }
    }

    private static List<Review> loadReviews(String inputFilePath) {
        List<Review> reviews = new ArrayList<>();
        try {
            Path path = Paths.get(inputFilePath);

            if (!Files.exists(path)) {
                System.out.println("Файл не найден: " + inputFilePath);
                return reviews;
            }
            if (!path.toString().endsWith(".json")) {
                System.out.println("Файл должен быть формата JSON: " + inputFilePath);
                return reviews;
            }

            try (BufferedReader reader = Files.newBufferedReader(path)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        JSONObject jsonObject = new JSONObject(line);
                        reviews.add(parseJsonObject(jsonObject));
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка чтения файла: " + e.getMessage());
        }
        return reviews;
    }

    private static Review parseJsonObject(JSONObject root) {
        String asin = root.optString("asin", "unknown");
        String reviewText = root.optString("reviewText", "");
        double overall = root.optDouble("overall", 0.0);
        long unixReviewTime = root.optLong("unixReviewTime", 0);
        int votes = root.has("vote") ? Integer.parseInt(root.getString("vote")) : 0;

        return new Review(asin, reviewText, overall, unixReviewTime, votes);
    }

    private static int getDisplayLimit(Scanner scanner) {
        System.out.println("Введите максимальное количество выводимых объектов:");
        int limit;
        while (true) {
            try {
                limit = Integer.parseInt(scanner.nextLine());
                if (limit > 0) {
                    break;
                }
                System.out.println("Число должно быть больше нуля. Попробуйте снова:");
            } catch (NumberFormatException e) {
                System.out.println("Ошибка ввода. Введите целое положительное число:");
            }
        }
        return limit;
    }

    private static List<String[]> listProductsByPopularity(List<Review> reviews, Scanner scanner) {
        int limit = getDisplayLimit(scanner);

        System.out.println("\nПродукты по популярности:");
        List<String[]> results = new ArrayList<>();
        reviews.stream()
                .collect(Collectors.groupingBy(Review::getAsin, Collectors.counting()))
                .entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                .limit(limit)
                .forEach(entry -> {
                    System.out.println(entry.getKey() + ": " + entry.getValue() + " отзывов");
                    results.add(new String[]{entry.getKey(), String.valueOf(entry.getValue())});
                });

        return results;
    }

    private static List<String[]> listProductsByWeightedRating(List<Review> reviews, Scanner scanner) {
        int limit = getDisplayLimit(scanner);

        System.out.println("\nПродукты по взвешенному рейтингу:");
        List<String[]> results = new ArrayList<>();
        reviews.stream()
                .collect(Collectors.groupingBy(Review::getAsin,
                        Collectors.averagingDouble(Review::getWeightedRating)))
                .entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .limit(limit)
                .forEach(entry -> {
                    System.out.printf("%s: %.2f рейтинг%n", entry.getKey(), entry.getValue());
                    results.add(new String[]{entry.getKey(), String.format("%.2f", entry.getValue())});
                });

        return results;
    }

    private static List<String[]> listMostPopularProductsInPeriod(List<Review> reviews, Scanner scanner) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
        System.out.println("\nВведите начальную дату (dd.MM.yyyy):");
        String startDateInput = scanner.nextLine();
        System.out.println("Введите конечную дату (dd.MM.yyyy):");
        String endDateInput = scanner.nextLine();

        List<String[]> results = new ArrayList<>();
        try {
            long startTime = dateFormat.parse(startDateInput).getTime() / 1000;
            long endTime = dateFormat.parse(endDateInput).getTime() / 1000;

            int limit = getDisplayLimit(scanner);

            System.out.println("\nСамые популярные продукты за указанный период:");
            reviews.stream()
                    .filter(r -> r.getUnixReviewTime() >= startTime && r.getUnixReviewTime() < endTime)
                    .collect(Collectors.groupingBy(Review::getAsin, Collectors.counting()))
                    .entrySet().stream()
                    .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                    .limit(limit)
                    .forEach(entry -> {
                        System.out.println(entry.getKey() + ": " + entry.getValue() + " отзывов");
                        results.add(new String[]{entry.getKey(), String.valueOf(entry.getValue())});
                    });
        } catch (ParseException e) {
            System.err.println("Ошибка: неверный формат даты");
        }

        return results;
    }

    private static List<String[]> searchProductByReviewText(List<Review> reviews, Scanner scanner) {
        System.out.println("\nВведите текст для поиска в отзывах: ");
        String searchText = scanner.nextLine();

        int limit = getDisplayLimit(scanner);

        System.out.println("\nНайденные отзывы:");
        List<String[]> results = new ArrayList<>();
        reviews.stream()
                .filter(r -> r.getReviewText().toLowerCase().contains(searchText.toLowerCase()))
                .limit(limit)
                .forEach(r -> {
                    System.out.println(r.getAsin() + ": " + r.getReviewText());
                    results.add(new String[]{r.getAsin(), r.getReviewText()});
                });

        return results;
    }

    private static String getFileNameWithoutExtension(String filePath) {
        Path path = Paths.get(filePath);
        return path.getFileName().toString().replaceFirst("[.][^.]+$", "");
    }

    private static void saveResultsToCSV(List<String[]> results, String fileNameWithoutExtension, String defaultCsvName, Scanner scanner) {
        System.out.println("Хотите сохранить результаты в CSV файл? (д/н): ");
        String saveChoice = scanner.nextLine().trim().toLowerCase();

        if (saveChoice.equals("д")) {
            String fileName = fileNameWithoutExtension + "_" + defaultCsvName;
            Path outputPath = Paths.get(fileName);

            try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
                for (String[] line : results) {
                    writer.write(String.join(";", line));
                    writer.newLine();
                }
                System.out.println("Результаты сохранены в файл: " + outputPath);
            } catch (IOException e) {
                System.err.println("Ошибка при сохранении в CSV: " + e.getMessage());
            }
        }
    }
}

class Review {
    private final String asin;
    private final String reviewText;
    private final double overall;
    private final long unixReviewTime;
    private final int votes;

    public Review(String asin, String reviewText, double overall, long unixReviewTime, int votes) {
        this.asin = asin;
        this.reviewText = reviewText;
        this.overall = overall;
        this.unixReviewTime = unixReviewTime;
        this.votes = votes;
    }

    public String getAsin() {
        return asin;
    }

    public String getReviewText() {
        return reviewText;
    }

    public double getOverall() {
        return overall;
    }

    public long getUnixReviewTime() {
        return unixReviewTime;
    }
    public double getWeightedRating() {
        return votes > 0 ? overall * votes : overall;
    }
}