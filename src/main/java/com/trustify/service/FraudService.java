package com.trustify.service;

import com.trustify.model.User;
import com.trustify.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FraudService {

    @Autowired
    UserRepository userRepository;

    // When dispute happens, we can check the user's history and if they have a pattern of disputes, we can penalize them by suspending their account or limiting their transactions.
    public void penalizeUser(String userId){
        User user = getUser(userId);
        user.setFraudScore(Math.max(0,user.getFraudScore() + 20)); // Increase fraud score by 20 points for each dispute, minimum 0.
        user.setSuccessfulTransactions(user.getSuccessfulTransactions() + 1); // Increment successful transactions to show that they have a history of transactions, even if some are disputed.
        user.setTotalTransactions(user.getTotalTransactions() + 1); // Increment total transactions to reflect the new transaction.

        recalculateTrust(user);
        userRepository.save(user);
    }

    // When Successful transaction happens, we can increase the user's fraud score to show that they have a history of successful transactions and are less likely to be fraudulent.
    public void rewardUser(String userId){
        User user = getUser(userId);
        user.setFraudScore(Math.max(0, user.getFraudScore() - 5)); // Decrease fraud score by 5 points for each successful transaction, minimum 0.
        user.setSuccessfulTransactions(user.getSuccessfulTransactions() + 1);
        user.setTotalTransactions(user.getTotalTransactions() + 1);

        recalculateTrust(user);

        userRepository.save(user);
    }

    // CORE LOGIC
    private void recalculateTrust(User user){
        int disputes = user.getDisputeCount();
        int success = user.getSuccessfulTransactions();

        double rating = 5.0;

        if(user.getTotalTransactions() > 0){
            double successRate = (double) success / user.getSuccessfulTransactions();

            rating = 5.0 * successRate; // Base rating on success rate, with a max of 5.0

            // penalty for disputes
            rating -= disputes * 0.3;
        }

        user.setTrustRating(Math.max(1.0, Math.min(5.0, rating)));
    }


    private User getUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

}
