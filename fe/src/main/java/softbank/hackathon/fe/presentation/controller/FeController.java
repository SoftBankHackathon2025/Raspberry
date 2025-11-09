package softbank.hackathon.fe.presentation.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class FeController {
	
	@GetMapping("/")
	public String home(Model model) {
		log.info("Home page accessed");
		model.addAttribute("page", "home");
		model.addAttribute("title", "Raspberry - 홈");
		return "home";
	}
	
	@GetMapping("/about")
	public String about(Model model) {
		log.info("About page accessed");
		model.addAttribute("page", "about");
		model.addAttribute("title", "Raspberry - 소개");
		model.addAttribute("description", "SoftBank Hackathon 2025 프로젝트입니다.");
		return "about";
	}
	
	@GetMapping("/contact")
	public String contact(Model model) {
		log.info("Contact page accessed");
		model.addAttribute("page", "contact");
		model.addAttribute("title", "Raspberry - 연락처");
		model.addAttribute("email", "contact@raspberry.com");
		model.addAttribute("phone", "+82-2-1234-5678");
		return "contact";
	}
	
	@GetMapping("/services")
	public String services(Model model) {
		log.info("Services page accessed");
		model.addAttribute("page", "services");
		model.addAttribute("title", "Raspberry - 서비스");
		return "services";
	}
	
	@GetMapping("/products")
	public String products(Model model) {
		log.info("Products page accessed");
		model.addAttribute("page", "products");
		model.addAttribute("title", "Raspberry - 제품");
		return "products";
	}
}
