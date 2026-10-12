package com.shopwise.app.demo;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.shopwise.app.catalog.entity.Product;
import com.shopwise.app.catalog.repository.ProductRepository;
import com.shopwise.app.sales.entity.Sale;
import com.shopwise.app.sales.repository.SaleRepository;

/**
 * Profil "demo" : epicerie "Chez Marie" avec un historique de ventes realiste sur 6 mois,
 * pour que le moteur de recommandation ait des habitudes d'achat a apprendre.
 *
 * Les paniers suivent des themes (petit-dejeuner, pates, apero, fromage et vin...) completes
 * par des achats isoles : le modele doit retrouver ces associations sans les connaitre.
 * Generation deterministe (graine fixe) ; rien n'est fait si des ventes existent deja.
 */
@Component
@Profile("demo")
@Order(10)
public class DemoDataLoader implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
	static final int SALES = 1500;
	private static final int DAYS = 180;

	/** nom, description, prix */
	private static final String[][] PRODUCTS = {
			{"Baguette tradition", "Pain frais cuit sur place, farine label rouge", "1.30"},
			{"Croissant pur beurre", "Viennoiserie pur beurre du matin", "1.20"},
			{"Beurre doux", "Beurre doux de Normandie 250 g", "2.90"},
			{"Confiture de fraises", "Confiture extra de fraises 370 g", "3.40"},
			{"Cafe moulu arabica", "Cafe moulu 100 % arabica 250 g", "4.90"},
			{"Lait demi-ecreme", "Lait demi-ecreme UHT 1 L", "1.15"},
			{"Spaghetti", "Pates spaghetti de ble dur 500 g", "1.60"},
			{"Sauce tomate basilic", "Sauce tomate cuisinee au basilic 400 g", "2.20"},
			{"Parmesan rape", "Fromage parmesan rape AOP 100 g", "3.10"},
			{"Huile d'olive vierge extra", "Huile d'olive vierge extra 75 cl", "8.50"},
			{"Chips nature", "Chips de pommes de terre nature 150 g", "1.90"},
			{"Olives vertes", "Olives vertes denoyautees 200 g", "2.40"},
			{"Saucisson sec", "Saucisson sec pur porc 250 g", "4.80"},
			{"Biere blonde", "Biere blonde artisanale 33 cl", "2.10"},
			{"Camembert de Normandie", "Fromage camembert au lait cru AOP 250 g", "3.90"},
			{"Vin rouge Bordeaux", "Vin rouge AOC Bordeaux 75 cl", "9.90"},
			{"Pain de campagne", "Pain de campagne au levain 500 g", "2.80"},
			{"Pommes Golden", "Pommes Golden de France 1 kg", "2.60"},
			{"Bananes", "Bananes Cavendish 1 kg", "1.99"},
			{"Yaourt nature", "Yaourts nature au lait entier 4 x 125 g", "1.80"},
			{"Oeufs frais", "Oeufs frais plein air x 6", "2.70"},
			{"Chocolat noir", "Tablette de chocolat noir 70 % cacao 100 g", "2.30"},
			{"Eau minerale", "Eau minerale naturelle 1,5 L", "0.65"},
			{"Riz basmati", "Riz basmati 1 kg", "3.20"},
	};

	/** Themes de paniers : indices dans PRODUCTS et probabilite de choisir le theme. */
	private static final int[][] THEMES = {
			{0, 1, 2, 3, 4, 5},  // petit-dejeuner
			{6, 7, 8, 9},        // pates
			{10, 11, 12, 13},    // apero
			{14, 15, 16},        // fromage et vin
			{17, 18, 19, 20},    // frais et fruits
	};
	private static final double[] THEME_WEIGHTS = {0.30, 0.25, 0.20, 0.10, 0.15};
	private static final int[] MISC = {21, 22, 23};

	private final ProductRepository productRepository;
	private final SaleRepository saleRepository;
	private final Clock clock;

	public DemoDataLoader(ProductRepository productRepository, SaleRepository saleRepository, Clock clock) {
		this.productRepository = productRepository;
		this.saleRepository = saleRepository;
		this.clock = clock;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (saleRepository.count() > 0) {
			log.info("Demo data skipped: sales already exist");
			return;
		}
		List<Product> products = new ArrayList<>();
		for (String[] p : PRODUCTS) {
			Product product = new Product();
			product.setName(p[0]);
			product.setDescription(p[1]);
			product.setPrice(new BigDecimal(p[2]));
			products.add(product);
		}
		List<Product> saved = productRepository.saveAll(products);

		Random random = new Random(2026);
		LocalDateTime now = LocalDateTime.now(clock);
		List<Sale> sales = new ArrayList<>(SALES);
		for (int i = 0; i < SALES; i++) {
			// Plus de ventes recentes que d'anciennes
			double age = Math.pow(random.nextDouble(), 1.5) * DAYS;
			Sale sale = new Sale(now.minusMinutes((long) (age * 24 * 60)));
			Map<Integer, Integer> basket = basket(random);
			basket.forEach((index, quantity) -> {
				Product product = saved.get(index);
				sale.addLine(product.getId(), product.getName(), quantity, product.getPrice());
			});
			sales.add(sale);
		}
		saleRepository.saveAll(sales);
		log.info("Demo data loaded: {} products and {} sales over {} days", saved.size(), SALES, DAYS);
	}

	private static Map<Integer, Integer> basket(Random random) {
		int[] theme = THEMES[pickTheme(random)];
		Set<Integer> items = new LinkedHashSet<>();
		for (int index : theme) {
			if (random.nextDouble() < 0.65) {
				items.add(index);
			}
		}
		while (items.size() < 2) {
			items.add(theme[random.nextInt(theme.length)]);
		}
		int extras = random.nextInt(3);
		for (int e = 0; e < extras; e++) {
			items.add(random.nextDouble() < 0.5 ? MISC[random.nextInt(MISC.length)] : random.nextInt(PRODUCTS.length));
		}
		Map<Integer, Integer> quantities = new LinkedHashMap<>();
		items.forEach(index -> quantities.put(index, 1 + random.nextInt(3)));
		return quantities;
	}

	private static int pickTheme(Random random) {
		double r = random.nextDouble();
		double cumulative = 0;
		for (int i = 0; i < THEME_WEIGHTS.length; i++) {
			cumulative += THEME_WEIGHTS[i];
			if (r < cumulative) {
				return i;
			}
		}
		return THEME_WEIGHTS.length - 1;
	}
}