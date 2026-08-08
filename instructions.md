Reactive Java Guide

part 1 - build service
-- Use Java 21
-- Build a web api using spring boot
-- make it a reactive web api
-- use spring data reactive to interact with postgres
-- use webflux to handle the reactive requests
-- build an event service with create, update, delete and get payments
-- build a service that can get payments based on a date range
-- the application should be able to get all payments or filter date or amount or payment status
-- there should be a healthcheck api 
-- I want the code to be well written, following best practices for reactive programming
-- use testcontainers for integration tests
-- I want to use maven to build the application
-- I want a guide-to-<class>.md file for each class created that explains the class and how it is used
-- use mapstruct for mapping between entities and dtos 
-- write a README.md file that explains the application and how to run it 


part 2 - deployment
-- I want to deploy the code to  k8s cluster running on my local orbstack 
-- I want you to write terraform files to deploy the application to k8s cluster running on my local orbstack
-- I also want to capture logs from the service on kibana 

part 2 - cloud deployment
-- I want to deploy this on GCP with authentication provided by a cloud provider