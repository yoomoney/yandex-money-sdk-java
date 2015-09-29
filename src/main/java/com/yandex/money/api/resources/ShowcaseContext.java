/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 NBCO Yandex.Money LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.yandex.money.api.resources;

import com.yandex.money.api.methods.JsonUtils;
import com.yandex.money.api.model.showcase.Showcase;
import com.yandex.money.api.net.ApiRequest;
import com.yandex.money.api.net.HostsProvider;
import com.yandex.money.api.net.PostRequest;
import com.yandex.money.api.typeadapters.GsonProvider;
import com.yandex.money.api.typeadapters.showcase.ShowcaseTypeAdapter;
import com.yandex.money.api.utils.HttpHeaders;
import com.yandex.money.api.utils.Strings;

import org.joda.time.DateTime;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Stack;

/**
 * This class handles {@link Showcase} submit steps.
 *
 * @author Slava Yasevich (vyasevich@yamoney.ru)
 */
public final class ShowcaseContext {

    /**
     * Processed steps so far.
     */
    private final Stack<Step> history;

    /**
     * {@link DateTime} of last showcase changes on remote server. Useful for caching.
     */
    private final DateTime lastModified;

    /**
     * Current step.
     */
    private Step currentStep;

    /**
     * Complete bundle of payment parameters. It's empty until the last step is reached.
     */
    private Map<String, String> params = Collections.emptyMap();

    /**
     * Current state (response code).
     */
    private State state = State.UNKNOWN;

    ShowcaseContext(State state) {
        this(null, null, new DateTime());
        this.state = state;
    }

    ShowcaseContext(Showcase showcase, String submitUrl, DateTime lastModified) {
        this.history = new Stack<>();
        this.currentStep = new Step(showcase, submitUrl);
        this.lastModified = lastModified;
    }

    /**
     * Constructor.
     *
     * @param history      previous steps
     * @param lastModified {@link DateTime} of last showcase changes on remote server
     * @param currentStep  current step
     * @param params       payment parameters
     * @param state        status code of current (last) operation
     */
    public ShowcaseContext(Stack<Step> history, DateTime lastModified, Step currentStep,
                           Map<String, String> params, State state) {

        if (history == null) {
            throw new NullPointerException("history is null");
        }
        if (lastModified == null) {
            throw new NullPointerException("lastModified is null");
        }
        if (params == null) {
            throw new NullPointerException("params is null");
        }
        this.history = history;
        this.lastModified = lastModified;
        this.currentStep = currentStep;
        this.params = params;
        this.state = state;
    }

    /**
     * @return request to move on the next state.
     */
    public ApiRequest<Showcase> createRequest() {
        return new Request(currentStep, lastModified);
    }

    /**
     * Pops previous step from history setting it as a current step.
     * <p/>
     * It will remove params if context has state {@code COMPLETED} and the state will be reset to
     * {@code HAS_NEXT_STEP}.
     * <p/>
     * If history is empty nothing will be done.
     *
     * @return previous step
     */
    public Step popStep() {
        if (!params.isEmpty()) {
            params = Collections.emptyMap();
            state = State.HAS_NEXT_STEP;
        } else if (!history.isEmpty()) {
            currentStep = history.pop();
        }
        return currentStep;
    }

    /**
     * @return size of processed steps
     */
    public int getHistorySize() {
        return history.size();
    }

    /**
     * @return reached steps
     */
    public Stack<Step> getHistory() {
        return history;
    }

    /**
     * @return current step
     */
    public Step getCurrentStep() {
        return currentStep;
    }

    void setCurrentStep(Step currentStep) {
        this.currentStep = currentStep;
    }

    /**
     * @return {@link DateTime} of last showcase changes on remote server
     */
    public DateTime getLastModified() {
        return lastModified;
    }

    /**
     * Collection of payment parameters which should be used in
     * {@link com.yandex.money.api.methods.RequestPayment} or
     * {@link com.yandex.money.api.methods.RequestExternalPayment}.
     *
     * @return payment parameters in case of last step or empty map otherwise
     */
    public Map<String, String> getParams() {
        return params;
    }

    void setParams(InputStream inputStream) {
        params = JsonUtils.map(GsonProvider.getGson()
                .toJsonTree(inputStream)
                .getAsJsonObject()
                .getAsJsonObject("params"));
    }

    /**
     * @return status code of current (last) operation
     */
    public State getState() {
        return state;
    }

    void setState(State state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return "ShowcaseContext{" +
                "history=" + history +
                ", lastModified=" + lastModified +
                ", currentStep=" + currentStep +
                ", params=" + params +
                ", state=" + state +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ShowcaseContext that = (ShowcaseContext) o;

        return history.equals(that.history) && lastModified.equals(that.lastModified)
                && currentStep.equals(that.currentStep)
                && params.equals(that.params)
                && state == that.state;

    }

    @Override
    public int hashCode() {
        int result = history.hashCode();
        result = 31 * result + lastModified.hashCode();
        result = 31 * result + currentStep.hashCode();
        result = 31 * result + params.hashCode();
        result = 31 * result + state.hashCode();
        return result;
    }

    /**
     * Possible states of {@link ShowcaseContext} instance
     */
    public enum State {
        HAS_NEXT_STEP,
        INVALID_PARAMS,
        COMPLETED,
        NOT_MODIFIED,
        UNKNOWN
    }

    public static final class Step {

        public final Showcase showcase;
        public final String submitUrl;

        public Step(Showcase showcase, String submitUrl) {
            this.showcase = showcase;
            this.submitUrl = submitUrl;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Step step = (Step) o;

            return !(showcase != null ? !showcase.equals(step.showcase) : step.showcase != null)
                    && !(submitUrl != null ? !submitUrl.equals(step.submitUrl)
                    : step.submitUrl != null);

        }

        @Override
        public int hashCode() {
            int result = showcase != null ? showcase.hashCode() : 0;
            result = 31 * result + (submitUrl != null ? submitUrl.hashCode() : 0);
            return result;
        }
    }

    private static final class Request extends PostRequest<Showcase> {

        private final String url;

        public Request(Step currentStep, DateTime lastModified) {
            super(Showcase.class, ShowcaseTypeAdapter.getInstance());
            if (currentStep == null) {
                throw new NullPointerException("currentStep is null");
            }
            if (currentStep.showcase == null) {
                throw new NullPointerException("showcase of current step is null");
            }
            if (Strings.isNullOrEmpty(currentStep.submitUrl)) {
                throw new IllegalArgumentException("url is null or empty");
            }
            this.url = currentStep.submitUrl;

            addHeader(HttpHeaders.IF_MODIFIED_SINCE, lastModified);
            addParameters(currentStep.showcase.getPaymentParameters());
        }

        @Override
        public String requestUrl(HostsProvider hostsProvider) {
            return url;
        }
    }

    /**
     * Pushes current step to {@code history} using new step as current step.
     *
     * @param newStep new step
     */
    void pushCurrentStep(Step newStep) {
        if (newStep == null) {
            throw new NullPointerException("new step is null");
        }
        history.push(currentStep);
        currentStep = newStep;
    }
}
