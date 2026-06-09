package com.example.webtestnreport.controller;

import com.example.webtestnreport.model.TestRun;
import com.example.webtestnreport.repository.TestRunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/runs")
@CrossOrigin(origins = "*")
public class TestRunController {

    @Autowired
    private TestRunRepository runRepository;

    @Value("${app.screenshots.dir:./data/screenshots}")
    private String screenshotsDir;

    @GetMapping
    public List<TestRun> getRecentRuns(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size) {
        return runRepository.findByOrderByStartedAtDesc(PageRequest.of(page, size));
    }

    @GetMapping("/rule/{ruleId}")
    public List<TestRun> getRunsByRule(@PathVariable Long ruleId) {
        return runRepository.findByRuleIdOrderByStartedAtDesc(ruleId);
    }

    @GetMapping("/screenshot/{fileName:.+}")
    public ResponseEntity<Resource> getScreenshot(@PathVariable String fileName) {
        try {
            Path path = Paths.get(screenshotsDir).resolve(fileName);
            Resource resource = new UrlResource(path.toUri());
            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
