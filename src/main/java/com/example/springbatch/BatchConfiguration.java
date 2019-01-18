package com.example.springbatch;


import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.transform.DelimitedLineAggregator;
import org.springframework.batch.item.xml.StaxEventItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;

@Configuration
@EnableBatchProcessing
public class BatchConfiguration {

    @Autowired
    public JobBuilderFactory jobBuilderFactory;

    @Autowired
    public StepBuilderFactory stepBuilderFactory;

    // tag::readerwriterprocessor[]
    @Bean
    public FlatFileItemReader<Person> reader() {
        return new FlatFileItemReaderBuilder<Person>()
                .name("personItemReader")
                .resource(new ClassPathResource("sample-data.csv"))
                .delimited()
                .names(new String[]{"lastName", "firstName"})
                .fieldSetMapper(new BeanWrapperFieldSetMapper<Person>() {{
                    setTargetType(Person.class);
                }})
                .build();
    }

    @Bean
    public PersonItemProcessor processor() {
        return new PersonItemProcessor();
    }

    @Bean
    public TaskExecutor taskExecutor() {
        SimpleAsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor();
        taskExecutor.setConcurrencyLimit(10);
        return taskExecutor;
    }

    @Bean
    public JdbcBatchItemWriter<Person> writer(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Person>()
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .sql("INSERT INTO people (first_name, last_name) VALUES (:firstName, :lastName)")
                .dataSource(dataSource)
                .build();
    }

    @Bean
    public FlatFileItemWriter<Person> writer2() {
        return new FlatFileItemWriterBuilder<Person>()
                .append(true)
                .name("new file")
                .saveState(true)
                .resource(new FileSystemResource("new file.csv"))
                .lineAggregator(new DelimitedLineAggregator<Person>())
                .build();
    }

    // end::readerwriterprocessor[]

    // tag::jobstep[]
    @Bean
    public Job importUserJob(JobCompletionNotificationListener listener, Step step1, Step step2, Step step3) {
        return jobBuilderFactory.get("importUserJob")
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .flow(step1)
                .next(step2)
                .next(step3)
                .end()
                .build();
    }

    @Bean
    public Step step1(JdbcBatchItemWriter<Person> writer) {
        return stepBuilderFactory.get("step1")
                .<Person, Person> chunk(5)
                .reader(reader())
                .processor(processor())
                .writer(writer).taskExecutor(taskExecutor())
                .build();
    }
    // end::jobstep[]


    @Bean
    public Step step2(FlatFileItemWriter<Person> writer2) {
        return stepBuilderFactory.get("step2")
                .<Person, Person> chunk(5)
                .reader(reader())
                .processor(processor())
                .writer(writer2).taskExecutor(taskExecutor())
                .build();
    }

    @Bean
    public Step step3(ItemWriter<Person> databaseXmlItemWriter) {
        return stepBuilderFactory.get("step3")
                .<Person, Person> chunk(5)
                .reader(reader())
                .processor(processor())
                .writer(databaseXmlItemWriter).taskExecutor(taskExecutor())
                .build();
    }

    @Bean
    public ItemWriter<Person> databaseXmlItemWriter() {
        StaxEventItemWriter<Person> xmlFileWriter = new StaxEventItemWriter<>();

        String exportFilePath = "students.xml";
        xmlFileWriter.setResource(new FileSystemResource(exportFilePath));

        xmlFileWriter.setRootTagName("students");

        Jaxb2Marshaller studentMarshaller = new Jaxb2Marshaller();
        studentMarshaller.setClassesToBeBound(new Class<?>[] { Person.class });
        xmlFileWriter.setMarshaller(studentMarshaller);

        return xmlFileWriter;
//
//        return new StaxEventItemWriter<Person>()
//                .setResource(new FileSystemResource("persons.xml"))
//                .setRootTagName("people")
//                .setMarshaller(new Jaxb2Marshaller().setClassesToBeBound(new Class<?>[] { Person.class }));
    }
}