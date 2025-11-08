package softbank.hackathon.fe.presentation.controller;

import org.springframework.stereotype.Controller;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class FeController {

    @GetMapping("/")
    public String home() {
        log.info("루트 페이지 요청 받음");
        return "index"; // templates/index.html을 반환
    }
    
}
