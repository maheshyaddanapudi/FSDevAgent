package com.ai.developer.tools.impl;

import com.ai.developer.tools.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.io.File;
import java.util.*;

@Slf4j
@Component
public class GitTool implements Tool {
    
    @Override
    public String getName() {
        return "git_operations";
    }
    
    @Override
    public String getDescription() {
        return "Perform Git operations on repositories";
    }
    
    @Override
    public Map<String, ParameterInfo> getParameters() {
        Map<String, ParameterInfo> params = new HashMap<>();
        
        params.put("operation", ParameterInfo.builder()
            .type("string")
            .description("Operation: init, clone, add, commit, push, pull, status, log, branch")
            .required(true)
            .build());
            
        params.put("path", ParameterInfo.builder()
            .type("string")
            .description("Repository path")
            .required(true)
            .build());
            
        params.put("message", ParameterInfo.builder()
            .type("string")
            .description("Commit message")
            .required(false)
            .build());
            
        params.put("url", ParameterInfo.builder()
            .type("string")
            .description("Remote repository URL")
            .required(false)
            .build());
            
        params.put("branch", ParameterInfo.builder()
            .type("string")
            .description("Branch name")
            .required(false)
            .build());
            
        return params;
    }
    
    @Override
    public Flux<ToolOutput> execute(Map<String, Object> arguments) {
        String operation = (String) arguments.get("operation");
        String path = (String) arguments.get("path");
        
        return Mono.fromCallable(() -> {
            switch (operation.toLowerCase()) {
                case "init":
                    return initRepository(path);
                case "clone":
                    return cloneRepository((String) arguments.get("url"), path);
                case "add":
                    return addFiles(path);
                case "commit":
                    return commitChanges(path, (String) arguments.get("message"));
                case "status":
                    return getStatus(path);
                case "log":
                    return getLog(path);
                case "branch":
                    return manageBranch(path, (String) arguments.get("branch"));
                default:
                    throw new IllegalArgumentException("Unknown operation: " + operation);
            }
        }).flux();
    }
    
    private ToolOutput initRepository(String path) throws GitAPIException {
        Git.init().setDirectory(new File(path)).call();
        return ToolOutput.builder()
                .type("git_init")
                .content("Initialized empty Git repository in " + path)
                .metadata(Map.of("path", path))
                .build();
    }
    
    private ToolOutput cloneRepository(String url, String path) throws GitAPIException {
        Git.cloneRepository()
                .setURI(url)
                .setDirectory(new File(path))
                .call();
        
        return ToolOutput.builder()
                .type("git_clone")
                .content("Cloned repository from " + url)
                .metadata(Map.of("url", url, "path", path))
                .build();
    }
    
    private ToolOutput addFiles(String path) throws Exception {
        try (Git git = Git.open(new File(path))) {
            git.add().addFilepattern(".").call();
            return ToolOutput.builder()
                    .type("git_add")
                    .content("Added all files to staging area")
                    .metadata(Map.of("path", path))
                    .build();
        }
    }
    
    private ToolOutput commitChanges(String path, String message) throws Exception {
        try (Git git = Git.open(new File(path))) {
            RevCommit commit = git.commit()
                    .setMessage(message != null ? message : "Auto-commit by AI Agent")
                    .call();
            
            return ToolOutput.builder()
                    .type("git_commit")
                    .content("Committed changes: " + commit.getId().getName())
                    .metadata(Map.of(
                        "commitId", commit.getId().getName(),
                        "message", commit.getFullMessage()
                    ))
                    .build();
        }
    }
    
    private ToolOutput getStatus(String path) throws Exception {
        try (Git git = Git.open(new File(path))) {
            Status status = git.status().call();
            
            Map<String, Object> statusInfo = new HashMap<>();
            statusInfo.put("added", status.getAdded());
            statusInfo.put("changed", status.getChanged());
            statusInfo.put("removed", status.getRemoved());
            statusInfo.put("untracked", status.getUntracked());
            statusInfo.put("modified", status.getModified());
            
            return ToolOutput.builder()
                    .type("git_status")
                    .content("Repository status retrieved")
                    .metadata(statusInfo)
                    .build();
        }
    }
    
    private ToolOutput getLog(String path) throws Exception {
        try (Git git = Git.open(new File(path))) {
            List<Map<String, String>> commits = new ArrayList<>();
            
            Iterable<RevCommit> log = git.log().setMaxCount(10).call();
            for (RevCommit commit : log) {
                commits.add(Map.of(
                    "id", commit.getId().getName(),
                    "message", commit.getShortMessage(),
                    "author", commit.getAuthorIdent().getName(),
                    "date", new Date(commit.getCommitTime() * 1000L).toString()
                ));
            }
            
            return ToolOutput.builder()
                    .type("git_log")
                    .content("Recent commits retrieved")
                    .metadata(Map.of("commits", commits))
                    .build();
        }
    }
    
    private ToolOutput manageBranch(String path, String branchName) throws Exception {
        try (Git git = Git.open(new File(path))) {
            if (branchName != null) {
                git.checkout().setName(branchName).setCreateBranch(true).call();
                return ToolOutput.builder()
                        .type("git_branch")
                        .content("Created and switched to branch: " + branchName)
                        .metadata(Map.of("branch", branchName))
                        .build();
            } else {
                List<String> branches = new ArrayList<>();
                git.branchList().call().forEach(ref -> 
                    branches.add(ref.getName().replace("refs/heads/", "")));
                
                return ToolOutput.builder()
                        .type("git_branch_list")
                        .content("Available branches")
                        .metadata(Map.of("branches", branches))
                        .build();
            }
        }
    }
}
