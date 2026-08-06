--
-- PostgreSQL database dump
--

\restrict ewntkIg9gtZuIcXdMO9gVRjn2JyX0bQ6lf6jTCFbmwxbWc2KJtiOkiLwBE8kOvX

-- Dumped from database version 16.14
-- Dumped by pg_dump version 16.14

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: cube; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS cube WITH SCHEMA public;


--
-- Name: EXTENSION cube; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION cube IS 'data type for multidimensional cubes';


--
-- Name: earthdistance; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS earthdistance WITH SCHEMA public;


--
-- Name: EXTENSION earthdistance; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION earthdistance IS 'calculate great-circle distances on the surface of the Earth';


--
-- Name: uuid-ossp; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;


--
-- Name: EXTENSION "uuid-ossp"; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION "uuid-ossp" IS 'generate universally unique identifiers (UUIDs)';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: approval_histories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.approval_histories (
    id bigint NOT NULL,
    request_id bigint NOT NULL,
    approver_id bigint NOT NULL,
    action character varying(20) NOT NULL,
    comment text,
    acted_at timestamp with time zone DEFAULT now() NOT NULL,
    request_type character varying(30) DEFAULT 'CHANGE_REQUEST'::character varying NOT NULL,
    CONSTRAINT chk_approval_action CHECK (((action)::text = ANY ((ARRAY['APPROVED'::character varying, 'REJECTED'::character varying, 'CANCELED'::character varying])::text[])))
);


--
-- Name: approval_histories_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.approval_histories_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: approval_histories_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.approval_histories_id_seq OWNED BY public.approval_histories.id;


--
-- Name: attendance_change_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.attendance_change_requests (
    id bigint NOT NULL,
    requester_id bigint NOT NULL,
    record_id bigint,
    target_date date NOT NULL,
    change_type character varying(30) NOT NULL,
    requested_check_in timestamp with time zone,
    requested_check_out timestamp with time zone,
    requested_workplace_id bigint,
    reason text,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    current_approver_id bigint,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_change_request_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'CANCELED'::character varying])::text[])))
);


--
-- Name: attendance_change_requests_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.attendance_change_requests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: attendance_change_requests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.attendance_change_requests_id_seq OWNED BY public.attendance_change_requests.id;


--
-- Name: attendance_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.attendance_events (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    record_id bigint,
    event_type character varying(20) NOT NULL,
    event_at timestamp with time zone NOT NULL,
    workplace_id bigint,
    latitude numeric(10,7),
    longitude numeric(10,7),
    accuracy_meters numeric(6,2),
    distance_meters integer,
    device_id character varying(100),
    device_platform character varying(20),
    mock_detected boolean DEFAULT false NOT NULL,
    raw_payload jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_event_type CHECK (((event_type)::text = ANY ((ARRAY['CHECK_IN'::character varying, 'CHECK_OUT'::character varying, 'BREAK_START'::character varying, 'BREAK_END'::character varying])::text[])))
);


--
-- Name: attendance_events_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.attendance_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: attendance_events_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.attendance_events_id_seq OWNED BY public.attendance_events.id;


--
-- Name: attendance_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.attendance_records (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    work_date date NOT NULL,
    status character varying(20) NOT NULL,
    check_in_at timestamp with time zone,
    check_out_at timestamp with time zone,
    workplace_id bigint,
    check_in_latitude numeric(10,7),
    check_in_longitude numeric(10,7),
    check_in_distance_meters integer,
    check_in_accuracy_meters numeric(6,2),
    check_out_latitude numeric(10,7),
    check_out_longitude numeric(10,7),
    check_out_distance_meters integer,
    work_minutes integer,
    break_minutes integer,
    overtime_minutes integer,
    is_late boolean DEFAULT false NOT NULL,
    is_early_leave boolean DEFAULT false NOT NULL,
    is_closed boolean DEFAULT false NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_attendance_status CHECK (((status)::text = ANY ((ARRAY['BEFORE_WORK'::character varying, 'WORKING'::character varying, 'BREAK'::character varying, 'FINISHED'::character varying, 'LATE'::character varying, 'EARLY_LEAVE'::character varying, 'ABSENT'::character varying, 'LEAVE'::character varying, 'OUTSIDE_WORK'::character varying, 'BUSINESS_TRIP'::character varying, 'REMOTE_WORK'::character varying])::text[]))),
    CONSTRAINT chk_check_in_lat CHECK (((check_in_latitude IS NULL) OR ((check_in_latitude >= ('-90'::integer)::numeric) AND (check_in_latitude <= (90)::numeric)))),
    CONSTRAINT chk_check_in_lon CHECK (((check_in_longitude IS NULL) OR ((check_in_longitude >= ('-180'::integer)::numeric) AND (check_in_longitude <= (180)::numeric)))),
    CONSTRAINT chk_check_out_lat CHECK (((check_out_latitude IS NULL) OR ((check_out_latitude >= ('-90'::integer)::numeric) AND (check_out_latitude <= (90)::numeric)))),
    CONSTRAINT chk_check_out_lon CHECK (((check_out_longitude IS NULL) OR ((check_out_longitude >= ('-180'::integer)::numeric) AND (check_out_longitude <= (180)::numeric))))
);


--
-- Name: attendance_records_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.attendance_records_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: attendance_records_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.attendance_records_id_seq OWNED BY public.attendance_records.id;


--
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_logs (
    id bigint NOT NULL,
    actor_id bigint,
    actor_email character varying(100),
    action character varying(100) NOT NULL,
    target_type character varying(50),
    target_id bigint,
    detail jsonb,
    ip_address character varying(45),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: audit_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.audit_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: audit_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.audit_logs_id_seq OWNED BY public.audit_logs.id;


--
-- Name: break_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.break_records (
    id bigint NOT NULL,
    record_id bigint NOT NULL,
    start_at timestamp with time zone NOT NULL,
    end_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: break_records_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.break_records_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: break_records_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.break_records_id_seq OWNED BY public.break_records.id;


--
-- Name: calendar_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.calendar_events (
    id bigint NOT NULL,
    title character varying(200) NOT NULL,
    start_at timestamp with time zone NOT NULL,
    end_at timestamp with time zone NOT NULL,
    all_day boolean DEFAULT false NOT NULL,
    description text,
    location character varying(200),
    color character varying(20),
    category character varying(20) DEFAULT 'ETC'::character varying NOT NULL,
    visibility character varying(20) DEFAULT 'ALL'::character varying NOT NULL,
    target_user_id bigint,
    created_by bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_calendar_events_category CHECK (((category)::text = ANY ((ARRAY['MEETING'::character varying, 'EVENT'::character varying, 'NOTICE'::character varying, 'ETC'::character varying])::text[]))),
    CONSTRAINT chk_calendar_events_visibility CHECK (((visibility)::text = ANY ((ARRAY['ALL'::character varying, 'PERSONAL'::character varying])::text[])))
);


--
-- Name: calendar_events_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.calendar_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: calendar_events_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.calendar_events_id_seq OWNED BY public.calendar_events.id;


--
-- Name: common_code_groups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.common_code_groups (
    id bigint NOT NULL,
    group_code character varying(50) NOT NULL,
    group_name character varying(100) NOT NULL,
    description character varying(200),
    protected boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: common_code_groups_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.common_code_groups_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: common_code_groups_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.common_code_groups_id_seq OWNED BY public.common_code_groups.id;


--
-- Name: common_codes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.common_codes (
    id bigint NOT NULL,
    group_code character varying(50) NOT NULL,
    code character varying(50) NOT NULL,
    code_name character varying(100) NOT NULL,
    description character varying(200),
    display_order integer DEFAULT 0 NOT NULL,
    active boolean DEFAULT true NOT NULL,
    protected boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: common_codes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.common_codes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: common_codes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.common_codes_id_seq OWNED BY public.common_codes.id;


--
-- Name: companies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.companies (
    id bigint NOT NULL,
    name character varying(100) NOT NULL,
    business_number character varying(20),
    address character varying(200),
    phone character varying(20),
    active boolean DEFAULT true NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: companies_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.companies_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: companies_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.companies_id_seq OWNED BY public.companies.id;


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: holidays; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.holidays (
    id bigint NOT NULL,
    holiday_date date NOT NULL,
    name character varying(100) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    holiday_type character varying(20) DEFAULT 'PUBLIC'::character varying NOT NULL
);


--
-- Name: holidays_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.holidays_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: holidays_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.holidays_id_seq OWNED BY public.holidays.id;


--
-- Name: leave_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.leave_requests (
    id bigint NOT NULL,
    requester_id bigint NOT NULL,
    request_type character varying(30) NOT NULL,
    start_at timestamp with time zone NOT NULL,
    end_at timestamp with time zone NOT NULL,
    reason text,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    current_approver_id bigint,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    employee_number character varying(6) NOT NULL,
    CONSTRAINT chk_leave_request_dates CHECK ((end_at >= start_at)),
    CONSTRAINT chk_leave_request_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'CANCELED'::character varying])::text[])))
);


--
-- Name: leave_requests_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.leave_requests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: leave_requests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.leave_requests_id_seq OWNED BY public.leave_requests.id;


--
-- Name: menu_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.menu_permissions (
    id bigint NOT NULL,
    role character varying(50) NOT NULL,
    menu_key character varying(50) NOT NULL,
    action_key character varying(50) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: menu_permissions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.menu_permissions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: menu_permissions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.menu_permissions_id_seq OWNED BY public.menu_permissions.id;


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notifications (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    type character varying(40) NOT NULL,
    title character varying(200) NOT NULL,
    message character varying(500) NOT NULL,
    related_type character varying(50),
    related_id bigint,
    is_read boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_notification_type CHECK (((type)::text = ANY ((ARRAY['CHANGE_REQUEST_SUBMITTED'::character varying, 'CHANGE_REQUEST_APPROVED'::character varying, 'CHANGE_REQUEST_REJECTED'::character varying, 'LEAVE_REQUEST_SUBMITTED'::character varying, 'LEAVE_REQUEST_APPROVED'::character varying, 'LEAVE_REQUEST_REJECTED'::character varying, 'OUTSIDE_WORK_REQUEST_SUBMITTED'::character varying, 'OUTSIDE_WORK_REQUEST_APPROVED'::character varying, 'OUTSIDE_WORK_REQUEST_REJECTED'::character varying, 'WORKPLACE_CHANGE_REQUEST_SUBMITTED'::character varying, 'WORKPLACE_CHANGE_REQUEST_APPROVED'::character varying, 'WORKPLACE_CHANGE_REQUEST_REJECTED'::character varying, 'WORK_SCHEDULE_CHANGE_REQUEST_SUBMITTED'::character varying, 'WORK_SCHEDULE_CHANGE_REQUEST_APPROVED'::character varying, 'WORK_SCHEDULE_CHANGE_REQUEST_REJECTED'::character varying, 'ATTENDANCE_CORRECTED'::character varying, 'ATTENDANCE_CLOSED'::character varying, 'ATTENDANCE_REOPENED'::character varying, 'GENERAL'::character varying])::text[])))
);


--
-- Name: notifications_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notifications_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notifications_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notifications_id_seq OWNED BY public.notifications.id;


--
-- Name: organizations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.organizations (
    id bigint NOT NULL,
    company_id bigint NOT NULL,
    parent_id bigint,
    name character varying(100) NOT NULL,
    display_order integer,
    active boolean DEFAULT true NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: organizations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.organizations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: organizations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.organizations_id_seq OWNED BY public.organizations.id;


--
-- Name: outside_work_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.outside_work_requests (
    id bigint NOT NULL,
    requester_id bigint NOT NULL,
    request_type character varying(30) NOT NULL,
    start_at timestamp with time zone NOT NULL,
    end_at timestamp with time zone NOT NULL,
    reason text,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    current_approver_id bigint,
    destination_address character varying(500),
    destination_latitude numeric(10,7),
    destination_longitude numeric(10,7),
    temp_radius_meters integer,
    visit_purpose character varying(500),
    client_name character varying(200),
    expected_return_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_outside_work_request_dates CHECK ((end_at >= start_at)),
    CONSTRAINT chk_outside_work_request_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'CANCELED'::character varying])::text[])))
);


--
-- Name: outside_work_requests_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.outside_work_requests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: outside_work_requests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.outside_work_requests_id_seq OWNED BY public.outside_work_requests.id;


--
-- Name: user_devices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_devices (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    device_id character varying(100) NOT NULL,
    device_platform character varying(20) NOT NULL,
    device_name character varying(100),
    fcm_token character varying(500),
    active boolean DEFAULT true NOT NULL,
    registered_at timestamp with time zone DEFAULT now() NOT NULL,
    last_seen_at timestamp with time zone,
    CONSTRAINT chk_device_platform CHECK (((device_platform)::text = ANY ((ARRAY['ANDROID'::character varying, 'IOS'::character varying])::text[])))
);


--
-- Name: user_devices_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_devices_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_devices_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.user_devices_id_seq OWNED BY public.user_devices.id;


--
-- Name: user_work_schedules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_work_schedules (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    work_schedule_id bigint NOT NULL,
    effective_from date DEFAULT CURRENT_DATE NOT NULL,
    effective_until date,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_effective_range CHECK (((effective_until IS NULL) OR (effective_until > effective_from)))
);


--
-- Name: user_work_schedules_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_work_schedules_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_work_schedules_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.user_work_schedules_id_seq OWNED BY public.user_work_schedules.id;


--
-- Name: user_workplaces; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_workplaces (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    workplace_id bigint NOT NULL,
    valid_from date,
    valid_to date,
    assigned_by bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: user_workplaces_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_workplaces_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_workplaces_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.user_workplaces_id_seq OWNED BY public.user_workplaces.id;


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    email character varying(100) NOT NULL,
    password character varying(200) NOT NULL,
    name character varying(50) NOT NULL,
    employee_number character varying(6),
    phone character varying(20),
    company_id bigint,
    organization_id bigint,
    job_title character varying(50),
    employment_type character varying(30),
    hire_date date,
    resign_date date,
    default_workplace_id bigint,
    work_schedule_id bigint,
    role character varying(20) DEFAULT 'EMPLOYEE'::character varying NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    failed_login_count integer DEFAULT 0 NOT NULL,
    must_change_password boolean DEFAULT false NOT NULL,
    level character varying(20) NOT NULL,
    CONSTRAINT chk_users_role CHECK (((role)::text = ANY ((ARRAY['EMPLOYEE'::character varying, 'MANAGER'::character varying, 'HR_ADMIN'::character varying, 'SYSTEM_ADMIN'::character varying])::text[]))),
    CONSTRAINT chk_users_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'LOCKED'::character varying])::text[])))
);


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: work_schedule_change_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.work_schedule_change_requests (
    id bigint NOT NULL,
    requester_id bigint NOT NULL,
    current_work_schedule_id bigint,
    target_work_schedule_id bigint NOT NULL,
    effective_month date NOT NULL,
    reason text NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    current_approver_id bigint,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: work_schedule_change_requests_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.work_schedule_change_requests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: work_schedule_change_requests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.work_schedule_change_requests_id_seq OWNED BY public.work_schedule_change_requests.id;


--
-- Name: work_schedules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.work_schedules (
    id bigint NOT NULL,
    company_id bigint NOT NULL,
    name character varying(100) NOT NULL,
    work_start_time time without time zone DEFAULT '09:00:00'::time without time zone NOT NULL,
    work_end_time time without time zone DEFAULT '18:00:00'::time without time zone NOT NULL,
    required_work_minutes integer DEFAULT 480 NOT NULL,
    overtime_threshold_min integer DEFAULT 480 NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    schedule_type character varying(30) DEFAULT 'FIXED'::character varying NOT NULL,
    late_threshold_minutes integer DEFAULT 0 NOT NULL,
    early_leave_threshold_minutes integer DEFAULT 0 NOT NULL,
    break_minutes integer DEFAULT 60 NOT NULL,
    night_shift_start time without time zone,
    night_shift_end time without time zone,
    holiday_work_threshold_minutes integer DEFAULT 0 NOT NULL,
    CONSTRAINT chk_required_minutes CHECK (((required_work_minutes > 0) AND (required_work_minutes <= 720))),
    CONSTRAINT chk_work_times CHECK ((work_start_time < work_end_time))
);


--
-- Name: work_schedules_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.work_schedules_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: work_schedules_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.work_schedules_id_seq OWNED BY public.work_schedules.id;


--
-- Name: workplace_change_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.workplace_change_requests (
    id bigint NOT NULL,
    requester_id bigint NOT NULL,
    current_workplace_id bigint,
    name character varying(100) NOT NULL,
    address character varying(200),
    detail_address character varying(200),
    type character varying(30) DEFAULT 'OFFICE'::character varying NOT NULL,
    latitude numeric(10,7) NOT NULL,
    longitude numeric(10,7) NOT NULL,
    radius_meters integer DEFAULT 100 NOT NULL,
    max_accuracy_meters integer,
    check_in_allowed boolean DEFAULT true NOT NULL,
    check_out_allowed boolean DEFAULT true NOT NULL,
    effective_date date NOT NULL,
    reason text NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    resulting_workplace_id bigint,
    current_approver_id bigint,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: workplace_change_requests_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.workplace_change_requests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: workplace_change_requests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.workplace_change_requests_id_seq OWNED BY public.workplace_change_requests.id;


--
-- Name: workplaces; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.workplaces (
    id bigint NOT NULL,
    company_id bigint NOT NULL,
    name character varying(100) NOT NULL,
    address character varying(200),
    latitude numeric(10,7) NOT NULL,
    longitude numeric(10,7) NOT NULL,
    radius_meters integer DEFAULT 100 NOT NULL,
    valid_from date,
    valid_to date,
    active boolean DEFAULT true NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    type character varying(30) DEFAULT 'OFFICE'::character varying NOT NULL,
    detail_address character varying(200),
    max_accuracy_meters integer,
    check_in_allowed boolean DEFAULT true NOT NULL,
    check_out_allowed boolean DEFAULT true NOT NULL,
    CONSTRAINT chk_workplace_latitude CHECK (((latitude >= ('-90'::integer)::numeric) AND (latitude <= (90)::numeric))),
    CONSTRAINT chk_workplace_longitude CHECK (((longitude >= ('-180'::integer)::numeric) AND (longitude <= (180)::numeric))),
    CONSTRAINT chk_workplace_radius CHECK ((radius_meters > 0))
);


--
-- Name: workplaces_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.workplaces_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: workplaces_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.workplaces_id_seq OWNED BY public.workplaces.id;


--
-- Name: approval_histories id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.approval_histories ALTER COLUMN id SET DEFAULT nextval('public.approval_histories_id_seq'::regclass);


--
-- Name: attendance_change_requests id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_change_requests ALTER COLUMN id SET DEFAULT nextval('public.attendance_change_requests_id_seq'::regclass);


--
-- Name: attendance_events id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_events ALTER COLUMN id SET DEFAULT nextval('public.attendance_events_id_seq'::regclass);


--
-- Name: attendance_records id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_records ALTER COLUMN id SET DEFAULT nextval('public.attendance_records_id_seq'::regclass);


--
-- Name: audit_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs ALTER COLUMN id SET DEFAULT nextval('public.audit_logs_id_seq'::regclass);


--
-- Name: break_records id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.break_records ALTER COLUMN id SET DEFAULT nextval('public.break_records_id_seq'::regclass);


--
-- Name: calendar_events id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calendar_events ALTER COLUMN id SET DEFAULT nextval('public.calendar_events_id_seq'::regclass);


--
-- Name: common_code_groups id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.common_code_groups ALTER COLUMN id SET DEFAULT nextval('public.common_code_groups_id_seq'::regclass);


--
-- Name: common_codes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.common_codes ALTER COLUMN id SET DEFAULT nextval('public.common_codes_id_seq'::regclass);


--
-- Name: companies id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.companies ALTER COLUMN id SET DEFAULT nextval('public.companies_id_seq'::regclass);


--
-- Name: holidays id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.holidays ALTER COLUMN id SET DEFAULT nextval('public.holidays_id_seq'::regclass);


--
-- Name: leave_requests id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_requests ALTER COLUMN id SET DEFAULT nextval('public.leave_requests_id_seq'::regclass);


--
-- Name: menu_permissions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_permissions ALTER COLUMN id SET DEFAULT nextval('public.menu_permissions_id_seq'::regclass);


--
-- Name: notifications id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications ALTER COLUMN id SET DEFAULT nextval('public.notifications_id_seq'::regclass);


--
-- Name: organizations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organizations ALTER COLUMN id SET DEFAULT nextval('public.organizations_id_seq'::regclass);


--
-- Name: outside_work_requests id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outside_work_requests ALTER COLUMN id SET DEFAULT nextval('public.outside_work_requests_id_seq'::regclass);


--
-- Name: user_devices id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_devices ALTER COLUMN id SET DEFAULT nextval('public.user_devices_id_seq'::regclass);


--
-- Name: user_work_schedules id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_work_schedules ALTER COLUMN id SET DEFAULT nextval('public.user_work_schedules_id_seq'::regclass);


--
-- Name: user_workplaces id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_workplaces ALTER COLUMN id SET DEFAULT nextval('public.user_workplaces_id_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Name: work_schedule_change_requests id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_schedule_change_requests ALTER COLUMN id SET DEFAULT nextval('public.work_schedule_change_requests_id_seq'::regclass);


--
-- Name: work_schedules id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_schedules ALTER COLUMN id SET DEFAULT nextval('public.work_schedules_id_seq'::regclass);


--
-- Name: workplace_change_requests id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workplace_change_requests ALTER COLUMN id SET DEFAULT nextval('public.workplace_change_requests_id_seq'::regclass);


--
-- Name: workplaces id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workplaces ALTER COLUMN id SET DEFAULT nextval('public.workplaces_id_seq'::regclass);


--
-- Name: approval_histories approval_histories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.approval_histories
    ADD CONSTRAINT approval_histories_pkey PRIMARY KEY (id);


--
-- Name: attendance_change_requests attendance_change_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_change_requests
    ADD CONSTRAINT attendance_change_requests_pkey PRIMARY KEY (id);


--
-- Name: attendance_events attendance_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_events
    ADD CONSTRAINT attendance_events_pkey PRIMARY KEY (id);


--
-- Name: attendance_records attendance_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_records
    ADD CONSTRAINT attendance_records_pkey PRIMARY KEY (id);


--
-- Name: attendance_records attendance_records_user_id_work_date_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_records
    ADD CONSTRAINT attendance_records_user_id_work_date_key UNIQUE (user_id, work_date);


--
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);


--
-- Name: break_records break_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.break_records
    ADD CONSTRAINT break_records_pkey PRIMARY KEY (id);


--
-- Name: calendar_events calendar_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calendar_events
    ADD CONSTRAINT calendar_events_pkey PRIMARY KEY (id);


--
-- Name: common_code_groups common_code_groups_group_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.common_code_groups
    ADD CONSTRAINT common_code_groups_group_code_key UNIQUE (group_code);


--
-- Name: common_code_groups common_code_groups_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.common_code_groups
    ADD CONSTRAINT common_code_groups_pkey PRIMARY KEY (id);


--
-- Name: common_codes common_codes_group_code_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.common_codes
    ADD CONSTRAINT common_codes_group_code_code_key UNIQUE (group_code, code);


--
-- Name: common_codes common_codes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.common_codes
    ADD CONSTRAINT common_codes_pkey PRIMARY KEY (id);


--
-- Name: companies companies_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.companies
    ADD CONSTRAINT companies_name_key UNIQUE (name);


--
-- Name: companies companies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.companies
    ADD CONSTRAINT companies_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: holidays holidays_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.holidays
    ADD CONSTRAINT holidays_pkey PRIMARY KEY (id);


--
-- Name: leave_requests leave_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_requests
    ADD CONSTRAINT leave_requests_pkey PRIMARY KEY (id);


--
-- Name: menu_permissions menu_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_permissions
    ADD CONSTRAINT menu_permissions_pkey PRIMARY KEY (id);


--
-- Name: menu_permissions menu_permissions_role_menu_key_action_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_permissions
    ADD CONSTRAINT menu_permissions_role_menu_key_action_key_key UNIQUE (role, menu_key, action_key);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: organizations organizations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organizations
    ADD CONSTRAINT organizations_pkey PRIMARY KEY (id);


--
-- Name: outside_work_requests outside_work_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outside_work_requests
    ADD CONSTRAINT outside_work_requests_pkey PRIMARY KEY (id);


--
-- Name: holidays uq_holidays_date; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.holidays
    ADD CONSTRAINT uq_holidays_date UNIQUE (holiday_date);


--
-- Name: user_devices user_devices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_devices
    ADD CONSTRAINT user_devices_pkey PRIMARY KEY (id);


--
-- Name: user_devices user_devices_user_id_device_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_devices
    ADD CONSTRAINT user_devices_user_id_device_id_key UNIQUE (user_id, device_id);


--
-- Name: user_work_schedules user_work_schedules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_work_schedules
    ADD CONSTRAINT user_work_schedules_pkey PRIMARY KEY (id);


--
-- Name: user_workplaces user_workplaces_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_workplaces
    ADD CONSTRAINT user_workplaces_pkey PRIMARY KEY (id);


--
-- Name: user_workplaces user_workplaces_user_id_workplace_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_workplaces
    ADD CONSTRAINT user_workplaces_user_id_workplace_id_key UNIQUE (user_id, workplace_id);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_employee_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_employee_number_key UNIQUE (employee_number);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: work_schedule_change_requests work_schedule_change_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_schedule_change_requests
    ADD CONSTRAINT work_schedule_change_requests_pkey PRIMARY KEY (id);


--
-- Name: work_schedules work_schedules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_schedules
    ADD CONSTRAINT work_schedules_pkey PRIMARY KEY (id);


--
-- Name: workplace_change_requests workplace_change_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workplace_change_requests
    ADD CONSTRAINT workplace_change_requests_pkey PRIMARY KEY (id);


--
-- Name: workplaces workplaces_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workplaces
    ADD CONSTRAINT workplaces_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_attendance_events_user_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_attendance_events_user_at ON public.attendance_events USING btree (user_id, event_at);


--
-- Name: idx_attendance_records_date_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_attendance_records_date_status ON public.attendance_records USING btree (work_date, status);


--
-- Name: idx_attendance_records_user_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_attendance_records_user_date ON public.attendance_records USING btree (user_id, work_date);


--
-- Name: idx_audit_logs_actor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_logs_actor ON public.audit_logs USING btree (actor_id, created_at);


--
-- Name: idx_audit_logs_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_logs_target ON public.audit_logs USING btree (target_type, target_id, created_at);


--
-- Name: idx_break_records_record; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_break_records_record ON public.break_records USING btree (record_id);


--
-- Name: idx_calendar_events_range; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_calendar_events_range ON public.calendar_events USING btree (start_at, end_at);


--
-- Name: idx_calendar_events_target_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_calendar_events_target_user ON public.calendar_events USING btree (target_user_id);


--
-- Name: idx_change_requests_requester; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_change_requests_requester ON public.attendance_change_requests USING btree (requester_id, status);


--
-- Name: idx_change_requests_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_change_requests_status ON public.attendance_change_requests USING btree (status, current_approver_id);


--
-- Name: idx_common_codes_group; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_common_codes_group ON public.common_codes USING btree (group_code, display_order);


--
-- Name: idx_leave_requests_period; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_leave_requests_period ON public.leave_requests USING btree (status, start_at, end_at);


--
-- Name: idx_leave_requests_requester; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_leave_requests_requester ON public.leave_requests USING btree (requester_id, status);


--
-- Name: idx_leave_requests_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_leave_requests_status ON public.leave_requests USING btree (status, current_approver_id);


--
-- Name: idx_menu_permissions_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_menu_permissions_role ON public.menu_permissions USING btree (role);


--
-- Name: idx_notifications_user_read; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notifications_user_read ON public.notifications USING btree (user_id, is_read, created_at);


--
-- Name: idx_organizations_company_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_organizations_company_id ON public.organizations USING btree (company_id, active);


--
-- Name: idx_organizations_parent_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_organizations_parent_id ON public.organizations USING btree (parent_id);


--
-- Name: idx_outside_work_requests_requester; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_outside_work_requests_requester ON public.outside_work_requests USING btree (requester_id, status);


--
-- Name: idx_outside_work_requests_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_outside_work_requests_status ON public.outside_work_requests USING btree (status, current_approver_id);


--
-- Name: idx_user_devices_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_devices_user_id ON public.user_devices USING btree (user_id, active);


--
-- Name: idx_user_work_schedules_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_work_schedules_user ON public.user_work_schedules USING btree (user_id, effective_from DESC);


--
-- Name: idx_user_workplaces_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_workplaces_user ON public.user_workplaces USING btree (user_id);


--
-- Name: idx_users_company_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_company_id ON public.users USING btree (company_id, status);


--
-- Name: idx_users_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_email ON public.users USING btree (email);


--
-- Name: idx_users_organization; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_organization ON public.users USING btree (organization_id);


--
-- Name: idx_work_schedule_change_requests_requester; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_work_schedule_change_requests_requester ON public.work_schedule_change_requests USING btree (requester_id);


--
-- Name: idx_work_schedule_change_requests_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_work_schedule_change_requests_status ON public.work_schedule_change_requests USING btree (status);


--
-- Name: idx_work_schedules_company; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_work_schedules_company ON public.work_schedules USING btree (company_id, active);


--
-- Name: idx_workplace_change_requests_requester; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_workplace_change_requests_requester ON public.workplace_change_requests USING btree (requester_id);


--
-- Name: idx_workplace_change_requests_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_workplace_change_requests_status ON public.workplace_change_requests USING btree (status);


--
-- Name: idx_workplaces_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_workplaces_active ON public.workplaces USING btree (active, valid_from, valid_to);


--
-- Name: idx_workplaces_company; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_workplaces_company ON public.workplaces USING btree (company_id, active);


--
-- Name: approval_histories approval_histories_approver_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.approval_histories
    ADD CONSTRAINT approval_histories_approver_id_fkey FOREIGN KEY (approver_id) REFERENCES public.users(id);


--
-- Name: attendance_change_requests attendance_change_requests_current_approver_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_change_requests
    ADD CONSTRAINT attendance_change_requests_current_approver_id_fkey FOREIGN KEY (current_approver_id) REFERENCES public.users(id);


--
-- Name: attendance_change_requests attendance_change_requests_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_change_requests
    ADD CONSTRAINT attendance_change_requests_record_id_fkey FOREIGN KEY (record_id) REFERENCES public.attendance_records(id);


--
-- Name: attendance_change_requests attendance_change_requests_requested_workplace_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_change_requests
    ADD CONSTRAINT attendance_change_requests_requested_workplace_id_fkey FOREIGN KEY (requested_workplace_id) REFERENCES public.workplaces(id);


--
-- Name: attendance_change_requests attendance_change_requests_requester_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_change_requests
    ADD CONSTRAINT attendance_change_requests_requester_id_fkey FOREIGN KEY (requester_id) REFERENCES public.users(id);


--
-- Name: attendance_events attendance_events_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_events
    ADD CONSTRAINT attendance_events_record_id_fkey FOREIGN KEY (record_id) REFERENCES public.attendance_records(id);


--
-- Name: attendance_events attendance_events_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_events
    ADD CONSTRAINT attendance_events_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: attendance_events attendance_events_workplace_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_events
    ADD CONSTRAINT attendance_events_workplace_id_fkey FOREIGN KEY (workplace_id) REFERENCES public.workplaces(id);


--
-- Name: attendance_records attendance_records_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_records
    ADD CONSTRAINT attendance_records_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: attendance_records attendance_records_workplace_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_records
    ADD CONSTRAINT attendance_records_workplace_id_fkey FOREIGN KEY (workplace_id) REFERENCES public.workplaces(id);


--
-- Name: audit_logs audit_logs_actor_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES public.users(id);


--
-- Name: break_records break_records_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.break_records
    ADD CONSTRAINT break_records_record_id_fkey FOREIGN KEY (record_id) REFERENCES public.attendance_records(id);


--
-- Name: calendar_events calendar_events_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calendar_events
    ADD CONSTRAINT calendar_events_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: calendar_events calendar_events_target_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calendar_events
    ADD CONSTRAINT calendar_events_target_user_id_fkey FOREIGN KEY (target_user_id) REFERENCES public.users(id);


--
-- Name: common_codes common_codes_group_code_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.common_codes
    ADD CONSTRAINT common_codes_group_code_fkey FOREIGN KEY (group_code) REFERENCES public.common_code_groups(group_code);


--
-- Name: users fk_users_default_workplace; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fk_users_default_workplace FOREIGN KEY (default_workplace_id) REFERENCES public.workplaces(id);


--
-- Name: leave_requests leave_requests_current_approver_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_requests
    ADD CONSTRAINT leave_requests_current_approver_id_fkey FOREIGN KEY (current_approver_id) REFERENCES public.users(id);


--
-- Name: leave_requests leave_requests_employee_number_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_requests
    ADD CONSTRAINT leave_requests_employee_number_fkey FOREIGN KEY (employee_number) REFERENCES public.users(employee_number);


--
-- Name: leave_requests leave_requests_requester_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_requests
    ADD CONSTRAINT leave_requests_requester_id_fkey FOREIGN KEY (requester_id) REFERENCES public.users(id);


--
-- Name: notifications notifications_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: organizations organizations_company_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organizations
    ADD CONSTRAINT organizations_company_id_fkey FOREIGN KEY (company_id) REFERENCES public.companies(id);


--
-- Name: organizations organizations_parent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organizations
    ADD CONSTRAINT organizations_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES public.organizations(id);


--
-- Name: outside_work_requests outside_work_requests_current_approver_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outside_work_requests
    ADD CONSTRAINT outside_work_requests_current_approver_id_fkey FOREIGN KEY (current_approver_id) REFERENCES public.users(id);


--
-- Name: outside_work_requests outside_work_requests_requester_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outside_work_requests
    ADD CONSTRAINT outside_work_requests_requester_id_fkey FOREIGN KEY (requester_id) REFERENCES public.users(id);


--
-- Name: user_devices user_devices_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_devices
    ADD CONSTRAINT user_devices_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: user_work_schedules user_work_schedules_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_work_schedules
    ADD CONSTRAINT user_work_schedules_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: user_work_schedules user_work_schedules_work_schedule_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_work_schedules
    ADD CONSTRAINT user_work_schedules_work_schedule_id_fkey FOREIGN KEY (work_schedule_id) REFERENCES public.work_schedules(id);


--
-- Name: user_workplaces user_workplaces_assigned_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_workplaces
    ADD CONSTRAINT user_workplaces_assigned_by_fkey FOREIGN KEY (assigned_by) REFERENCES public.users(id);


--
-- Name: user_workplaces user_workplaces_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_workplaces
    ADD CONSTRAINT user_workplaces_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: user_workplaces user_workplaces_workplace_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_workplaces
    ADD CONSTRAINT user_workplaces_workplace_id_fkey FOREIGN KEY (workplace_id) REFERENCES public.workplaces(id);


--
-- Name: users users_company_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_company_id_fkey FOREIGN KEY (company_id) REFERENCES public.companies(id);


--
-- Name: users users_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: work_schedule_change_requests work_schedule_change_requests_current_approver_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_schedule_change_requests
    ADD CONSTRAINT work_schedule_change_requests_current_approver_id_fkey FOREIGN KEY (current_approver_id) REFERENCES public.users(id);


--
-- Name: work_schedule_change_requests work_schedule_change_requests_current_work_schedule_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_schedule_change_requests
    ADD CONSTRAINT work_schedule_change_requests_current_work_schedule_id_fkey FOREIGN KEY (current_work_schedule_id) REFERENCES public.work_schedules(id);


--
-- Name: work_schedule_change_requests work_schedule_change_requests_requester_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_schedule_change_requests
    ADD CONSTRAINT work_schedule_change_requests_requester_id_fkey FOREIGN KEY (requester_id) REFERENCES public.users(id);


--
-- Name: work_schedule_change_requests work_schedule_change_requests_target_work_schedule_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_schedule_change_requests
    ADD CONSTRAINT work_schedule_change_requests_target_work_schedule_id_fkey FOREIGN KEY (target_work_schedule_id) REFERENCES public.work_schedules(id);


--
-- Name: work_schedules work_schedules_company_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_schedules
    ADD CONSTRAINT work_schedules_company_id_fkey FOREIGN KEY (company_id) REFERENCES public.companies(id);


--
-- Name: workplace_change_requests workplace_change_requests_current_approver_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workplace_change_requests
    ADD CONSTRAINT workplace_change_requests_current_approver_id_fkey FOREIGN KEY (current_approver_id) REFERENCES public.users(id);


--
-- Name: workplace_change_requests workplace_change_requests_current_workplace_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workplace_change_requests
    ADD CONSTRAINT workplace_change_requests_current_workplace_id_fkey FOREIGN KEY (current_workplace_id) REFERENCES public.workplaces(id);


--
-- Name: workplace_change_requests workplace_change_requests_requester_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workplace_change_requests
    ADD CONSTRAINT workplace_change_requests_requester_id_fkey FOREIGN KEY (requester_id) REFERENCES public.users(id);


--
-- Name: workplace_change_requests workplace_change_requests_resulting_workplace_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workplace_change_requests
    ADD CONSTRAINT workplace_change_requests_resulting_workplace_id_fkey FOREIGN KEY (resulting_workplace_id) REFERENCES public.workplaces(id);


--
-- Name: workplaces workplaces_company_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workplaces
    ADD CONSTRAINT workplaces_company_id_fkey FOREIGN KEY (company_id) REFERENCES public.companies(id);


--
-- PostgreSQL database dump complete
--

\unrestrict ewntkIg9gtZuIcXdMO9gVRjn2JyX0bQ6lf6jTCFbmwxbWc2KJtiOkiLwBE8kOvX

