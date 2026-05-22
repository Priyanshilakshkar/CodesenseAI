package com.codeSense_Ai.Analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class DashboardController {
    private final DashboardDataBuilder dataBuilder;

    /**
     * Main dashboard overview page.
     * GET /dashboard  or  GET /dashboard?days=7
     */
    @GetMapping({"", "/"})
    public String dashboard(
            @RequestParam(defaultValue = "30") int days,
            Model model) {
        model.addAllAttributes(dataBuilder.buildDashboardModel(days));
        return "dashboard/index";  // → templates/dashboard/index.html
    }

    /**
     * All reviews list.
     * GET /dashboard/reviews
     */
    @GetMapping("/reviews")
    public String reviewsList(Model model) {
        model.addAllAttributes(dataBuilder.buildReviewsListModel());
        return "dashboard/reviews";  // → templates/dashboard/reviews.html
    }

    /**
     * Single review detail with all inline comments.
     * GET /dashboard/reviews/{id}
     */
    @GetMapping("/reviews/{id}")
    public String reviewDetail(@PathVariable Long id, Model model) {
        Map<String, Object> data = dataBuilder.buildReviewDetailModel(id);
        if (data.isEmpty()) {
            return "redirect:/dashboard/reviews"; // review not found
        }
        model.addAllAttributes(data);
        return "dashboard/review-detail";
    }

    /**
     * Repositories list with stats.
     * GET /dashboard/repositories
     */
    @GetMapping("/repositories")
    @PreAuthorize("hasRole('ADMIN') or hasRole('REPO_OWNER')")
    public String repositoriesList(Model model) {
        model.addAttribute("topRepos",
                dataBuilder.buildDashboardModel(30).get("topRepos"));
        return "dashboard/repositories";
    }
}
