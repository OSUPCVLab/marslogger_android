//
// Created by xiang on 2021/10/11.
//

#ifndef LASER_LOGGER_UTILS_H
#define LASER_LOGGER_UTILS_H

// #include <glog/logging.h>
#include <chrono>
#include <fstream>
#include <iostream>
#include <map>
#include <numeric>
#include <string>
#include <vector>

namespace laser_logger {

/// timer
class Timer {
   public:
    struct TimerRecord {
        TimerRecord() = default;
        TimerRecord(const std::string& name, double time_usage) {
            func_name_ = name;
            time_usage_in_ms_.emplace_back(time_usage);
        }
        std::string func_name_;
        std::vector<double> time_usage_in_ms_;
    };

    /**
     * call F and save its time usage
     * @tparam F
     * @param func
     * @param func_name
     */
    template <class F>
    static void Evaluate(F&& func, const std::string& func_name) {
        auto t1 = std::chrono::high_resolution_clock::now();
        std::forward<F>(func)();
        auto t2 = std::chrono::high_resolution_clock::now();
        auto time_used = std::chrono::duration_cast<std::chrono::duration<double>>(t2 - t1).count() * 1000;

        if (records_.find(func_name) != records_.end()) {
            records_[func_name].time_usage_in_ms_.emplace_back(time_used);
        } else {
            records_.insert({func_name, TimerRecord(func_name, time_used)});
        }
    }

    static void addRecord(const std::string& func_name, double elapsed_ms) {
        if (records_.find(func_name) != records_.end()) {
            records_[func_name].time_usage_in_ms_.emplace_back(elapsed_ms);
        } else {
            records_.insert({func_name, TimerRecord(func_name, elapsed_ms)});
        }
    }

    /// print the run time
    static void PrintAll() {
        std::cout << ">>> ===== Printing run time =====";
        for (const auto& r : records_) {
            std::cout << "> [ " << r.first << " ] average time usage: "
                      << std::accumulate(r.second.time_usage_in_ms_.begin(), r.second.time_usage_in_ms_.end(), 0.0) /
                             double(r.second.time_usage_in_ms_.size())
                      << " ms , called times: " << r.second.time_usage_in_ms_.size();
        }
        std::cout << ">>> ===== Printing run time end =====";
    }

    /// dump to a log file
    static void DumpIntoFile(const std::string& file_name) {
        std::ofstream ofs(file_name, std::ios::out);
        if (!ofs.is_open()) {
            std::cout << "Failed to open file: " << file_name;
            return;
        } else {
            std::cout << "Dump Time Records into file: " << file_name;
        }

        std::cout << ">>> ===== Printing run time =====";
        for (const auto& r : records_) {
            ofs << "> [ " << r.first << " ] average time usage: "
                << std::accumulate(r.second.time_usage_in_ms_.begin(), r.second.time_usage_in_ms_.end(), 0.0) /
                        double(r.second.time_usage_in_ms_.size())
                << " ms , called times: " << r.second.time_usage_in_ms_.size() << "\n";
        }
        ofs << "\n";
        std::cout << ">>> ===== Printing run time end =====";

        size_t max_length = 0;
        for (const auto& iter : records_) {
            ofs << iter.first << ", ";
            if (iter.second.time_usage_in_ms_.size() > max_length) {
                max_length = iter.second.time_usage_in_ms_.size();
            }
        }
        ofs << std::endl;

        for (size_t i = 0; i < max_length; ++i) {
            for (const auto& iter : records_) {
                if (i < iter.second.time_usage_in_ms_.size()) {
                    ofs << iter.second.time_usage_in_ms_[i] << ",";
                } else {
                    ofs << ",";
                }
            }
            ofs << std::endl;
        }
        ofs.close();
    }

    /// get the average time usage of a function
    static double GetMeanTime(const std::string& func_name) {
        if (records_.find(func_name) == records_.end()) {
            return 0.0;
        }

        auto r = records_[func_name];
        return std::accumulate(r.time_usage_in_ms_.begin(), r.time_usage_in_ms_.end(), 0.0) /
               double(r.time_usage_in_ms_.size());
    }

    /// clean the records
    static void Clear() { records_.clear(); }

   private:
    static std::map<std::string, TimerRecord> records_;
};

}  // namespace laser_logger

#endif  // LASER_LOGGER_UTILS_H
