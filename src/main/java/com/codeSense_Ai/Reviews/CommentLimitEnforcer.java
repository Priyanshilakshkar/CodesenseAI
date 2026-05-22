package com.codeSense_Ai.Reviews;

import com.codeSense_Ai.model.ReviewIssue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@Slf4j
public class CommentLimitEnforcer {

    public EnforcedResult enforce(List<ReviewIssue> issues , int maxLimit){
        if(issues.size()< maxLimit){
            return new EnforcedResult(issues , 0);
        }

        List<ReviewIssue> sorted = issues.stream().sorted(
                Comparator.<ReviewIssue ,Boolean>comparing(ReviewIssue::isSecurity).reversed()

                        .thenComparingInt(ReviewIssue::severity).reversed()
                        .thenComparingInt(ReviewIssue::confidence).reversed()
        ).toList();
        List<ReviewIssue> kept = sorted.subList(0 , maxLimit);
        int dropped  = sorted.size() - kept.size();

        log.info("Comment limit enforced: kept {}/{} issues ({} dropped), limit={}",
                kept.size(), issues.size(), dropped, maxLimit);


        return new EnforcedResult(kept , dropped);
    }


    public record EnforcedResult(
            List<ReviewIssue> issues,   // issues to actually post
            int droppedCount            // how many were over the limit
    ) {
        public boolean hadOverflow() {
            return droppedCount > 0;
        }
    }
}
